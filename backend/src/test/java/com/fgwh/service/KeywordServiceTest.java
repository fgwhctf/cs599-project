package com.fgwh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordServiceTest {

    private final KeywordService keywordService = new KeywordService();

    @Test
    void keepsOriginalQueryAndSplitsChinesePunctuation() {
        String query = "统计广东、华东 GMV，按品类排名前10";

        List<String> keywords = keywordService.extract(query);

        assertEquals(query, keywords.getFirst());
        assertTrue(keywords.contains("统计广东"));
        assertTrue(keywords.contains("华东"));
        assertTrue(keywords.contains("GMV"));
        assertTrue(keywords.contains("按品类排名前10"));
    }

    @Test
    void removesDuplicateKeywordsAndKeepsEncounterOrder() {
        List<String> keywords = keywordService.extract("GMV GMV 广东");

        assertEquals(List.of("GMV GMV 广东", "GMV", "广东"), keywords);
    }
}
