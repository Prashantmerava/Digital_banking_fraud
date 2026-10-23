package com.banking.frauddetectionservice.controller;

import com.banking.frauddetectionservice.model.FraudCheckResult;
import com.banking.frauddetectionservice.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/fraud")
@RequiredArgsConstructor
public class FraudCheckController {
    private final FraudDetectionService fraudDetectionService;

    @PostMapping("/check")
    public FraudCheckResult check(@RequestBody Map<String, Object> transaction) {
        return fraudDetectionService.checkTransaction(transaction);
    }
}
