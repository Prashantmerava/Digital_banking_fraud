package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.client.FraudDetectionClient;
import com.banking.transactionservice.dto.FraudCheckResponse;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.model.Transaction;
import com.banking.transactionservice.model.TransactionStatus;
import com.banking.transactionservice.model.TransactionType;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final FraudDetectionClient fraudDetectionClient;

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        if (request.getSenderAccountNumber().equals(request.getReceiverAccountNumber())) {
            throw new IllegalArgumentException("Sender and receiver accounts must be different");
        }
        log.info("SAGA START - Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(), request.getReceiverAccountNumber(), request.getAmount());

        // Preserve the original Saga's first step: reserve funds from the sender.
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        transaction = transactionRepository.save(transaction);

        Map<String, Object> event = new HashMap<>();
        event.put("transactionId", transaction.getId());
        event.put("senderAccountNumber", transaction.getSenderAccountNumber());
        event.put("receiverAccountNumber", transaction.getReceiverAccountNumber());
        event.put("amount", transaction.getAmount());
        event.put("description", transaction.getDescription());

        FraudCheckResponse result;
        try {
            result = fraudDetectionClient.checkTransaction(event);
        } catch (Exception ex) {
            log.error("Fraud check failed for transaction {}. Compensating sender debit.", transaction.getId(), ex);
            compensateTransaction(transaction,
                    "Fraud check service unavailable; transaction cancelled and amount refunded");
            return mapToResponse(transactionRepository.findById(transaction.getId()).orElse(transaction));
        }

        if (result.isFraud()) {
            compensateTransaction(transaction,
                    "Fraud detected: " + result.getReason()
                            + "; transaction cancelled and sender refunded");
        } else {
            completeTransaction(transaction);
        }
        return mapToResponse(transactionRepository.findById(transaction.getId()).orElse(transaction));
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED || transaction.getStatus() == TransactionStatus.FLAGGED) return;
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(), transaction.getAmount());
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason + " - SAGA compensation executed at " + LocalDateTime.now());
        transactionRepository.save(transaction);

    }

    @Transactional
    public void completeTransaction(Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED) return;
        // Credit the receiver before marking the transaction completed.
        accountServiceClient.creditBalance(transaction.getReceiverAccountNumber(), transaction.getAmount());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());
        return response;
    }
}
