package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final ConcurrentHashMap<String, VelocityWindow> velocityWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BigDecimal> averageAmounts = new ConcurrentHashMap<>();

    @Value("${fraud.max-transactions-per-minute}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    public FraudCheckResult checkTransaction(Map<String,Object> payload){
        String transactionId = (String)payload.get("transactionId");
        String accountNumber = (String)payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());


        // Fetch real balance from Account Service
        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);

        log.info("Checking transaction: {} account: {} amount: {} balance: {}",
                transactionId, accountNumber, amount, senderBalance);

        FraudCheckResult result = performFraudChecks(accountNumber, amount, senderBalance);

        return result;
    }

    private FraudCheckResult performFraudChecks(
            String accountNumber,
            BigDecimal amount,
            BigDecimal senderBalance) {

        // Pattern 1: Velocity Check
        if(isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(
                    true, "Too many transactions in 60 seconds "+
                    " - Velocity limit exceeded");
        }

        // Pattern 2: Amount check
        if(isAmountSuspicious(accountNumber, amount)){
            return new FraudCheckResult(
                    true, "Unusual transaction amount "+
                    " - exceeds 3x your average");
        }

        // Pattern 3: Balance Check
        if(senderBalance.compareTo(BigDecimal.ZERO) > 0
                && isBalanceCheckFailed(senderBalance, amount)){
            return new FraudCheckResult(
                    true, "Transaction exceed 90% of account balance");
        }

        return new FraudCheckResult(false, null);
    }


    private boolean isVelocityExceeded(String accountNumber){
        long now = System.currentTimeMillis();
        VelocityWindow window = velocityWindows.compute(accountNumber, (key, current) -> {
            if (current == null || now - current.startedAtMillis >= 60_000L) {
                return new VelocityWindow(now, 1L);
            }
            current.count++;
            return current;
        });
        long count = window.count;
        log.info("Velocity check - account: {} count: {}/{}", accountNumber, count, maxTransactionsPerMinute);
        return count > maxTransactionsPerMinute;
    }

    // Patter 2: if current amount > 3x average

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount){
        final boolean[] suspicious = {false};
        averageAmounts.compute(accountNumber, (key, currentAverage) -> {
            if (currentAverage == null) return amount;
            BigDecimal threshold = currentAverage.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));
            suspicious[0] = amount.compareTo(threshold) > 0;
            BigDecimal newAverage = currentAverage.add(amount)
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            log.info("Amount check - amount: {} threshold: {} suspicious: {}", amount, threshold, suspicious[0]);
            return newAverage;
        });
        return suspicious[0];
    }

    private static final class VelocityWindow {
        private final long startedAtMillis;
        private long count;
        private VelocityWindow(long startedAtMillis, long count) {
            this.startedAtMillis = startedAtMillis;
            this.count = count;
        }
    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount){

        BigDecimal maxAllowed = senderBalance.multiply(
                BigDecimal.valueOf(maxBalancePercentage));

        log.info("Balance check - amount: {} maxAllowed: {} suscpious: {}",
                amount, maxAllowed, amount.compareTo(maxAllowed) > 0);

        return amount.compareTo(maxAllowed) > 0;
    }























}
