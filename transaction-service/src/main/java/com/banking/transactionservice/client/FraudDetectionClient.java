package com.banking.transactionservice.client;

import com.banking.transactionservice.dto.FraudCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "fraud-detection-service", url = "${fraud.service.url:http://localhost:8084}")
public interface FraudDetectionClient {
    @PostMapping("/internal/fraud/check")
    FraudCheckResponse checkTransaction(@RequestBody Map<String, Object> transaction);
}
