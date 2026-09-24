package org.hrcopilot.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hrcopilot.agents.AgentRecords.CandidateProfileDetails;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts display-only resume facts. */
@Component
public class CandidateProfileAnalyzer {

    private static final Pattern EDUCATION = Pattern.compile("(?i)\\b(?:bachelor(?:'s)?|b\\.?s\\.?c?\\.?|b\\.?a\\.?|b\\.?e\\.?|b\\.?tech|master(?:'s)?|m\\.?s\\.?c?\\.?|m\\.?a\\.?|m\\.?tech|mba|ph\\.?d\\.?|doctorate|associate(?:'s)?|diploma|high school|secondary school)\\b");
    private static final Pattern YEARS = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)\\s*\\+?\\s*(?:years?|yrs?)\\b");
    private static final java.util.Map<String,Double> WORD_NUMBERS = java.util.Map.of("one",1d,"two",2d,"three",3d,"four",4d,"five",5d,"six",6d,"seven",7d,"eight",8d,"nine",9d,"ten",10d);
    private final ObjectMapper mapper;

    public CandidateProfileAnalyzer(ObjectMapper mapper) {
        this.mapper=mapper;
    }

    public CandidateProfileDetails analyze(String name, String role, String profileJson) {
        String text = profileText(profileJson);
        String education = education(text);
        Double years = years(text);
        boolean assumed = (years==null);
        return new CandidateProfileDetails(
                name,
                role,
                education,
                assumed ? 0 : years,
                assumed,
            assumed ? "Experience duration was unclear in the resume; freshman/0 years assumed for display.":"Explicit duration found in resume."
        );
    }

    private String profileText(String profileJson) {
        try {
            JsonNode root=mapper.readTree(profileJson);
            String extracted=root.path("extractedText").asText("");
            return extracted.isBlank()?root.toString():extracted;
        }
        catch(Exception ignored) {
            return profileJson==null?"":profileJson;
        }
    }

    private String education(String text) {

        for(String line:text.split("\\R")) {

            String candidate=line.replaceAll("^[\\s•*#\\-]+", "").trim();
            Matcher degree=EDUCATION.matcher(candidate);

            if(!candidate.isBlank() && degree.find()) {
                int sentenceEnd=candidate.length();
                for(int i=degree.end();i<candidate.length();i++) {
                    char c=candidate.charAt(i);
                    if((c=='.'||c=='!'||c=='?') && (i+1==candidate.length() || Character.isWhitespace(candidate.charAt(i+1)))) { sentenceEnd=i; break; }
                }
                candidate=candidate.substring(0,sentenceEnd).trim();
                return candidate.length()>220?candidate.substring(0,220)+"…":candidate;
            }
        }
        return "Not clearly stated in resume";
    }

    private Double years(String text) {
        Matcher matcher=YEARS.matcher(text);
        double largest=0; boolean found=false;
        while(matcher.find()) {
            String token=matcher.group(1).toLowerCase(Locale.ROOT);
            double value=WORD_NUMBERS.getOrDefault(token,parse(token));
            if(value>=0 && value<=60) { largest=Math.max(largest,value); found=true; }
        }
        return found?largest:null;
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        }
        catch(NumberFormatException ignored) {
            return -1;
        }
    }
}
