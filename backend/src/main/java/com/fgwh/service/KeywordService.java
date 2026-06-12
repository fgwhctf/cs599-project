package com.fgwh.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class KeywordService {

    private static final Pattern SPLIT = Pattern.compile("[\\s,，。！？?；;:：、（）()\\[\\]{}]+");

    public List<String> extract(String query) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(query);
        for (String token : SPLIT.split(query)) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return new ArrayList<>(keywords);
    }
}
