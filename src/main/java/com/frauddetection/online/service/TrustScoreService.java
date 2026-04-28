package com.frauddetection.online.service;

import org.springframework.stereotype.Service;

@Service
public class TrustScoreService {

    public int calculateTrustScore(long fraudCount, double avgAmount) {
        int score = 100;

        if (fraudCount > 5) score -= 40;
        if (avgAmount > 10000) score -= 20;

        return Math.max(score, 0);
    }

    public String getRiskLevel(int score) {
        if (score >= 80) return "Low";
        if (score >= 50) return "Medium";
        return "High";
    }
}