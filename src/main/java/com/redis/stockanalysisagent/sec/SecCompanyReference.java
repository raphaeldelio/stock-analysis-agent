package com.redis.stockanalysisagent.sec;

public record SecCompanyReference(
        String ticker,
        String companyName,
        String cik
) {
}
