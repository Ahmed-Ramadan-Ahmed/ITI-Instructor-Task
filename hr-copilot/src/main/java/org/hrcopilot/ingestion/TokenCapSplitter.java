package org.hrcopilot.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Approximate token-window splitter: up to 500 whitespace tokens with 50-token overlap. */
@Component
public class TokenCapSplitter {
    private static final int WINDOW=500, OVERLAP=50;
    public List<Document> split(List<Document> documents) {

        List<Document> out=new ArrayList<>();

        for(Document document:documents){

            String text=document.getText()==null?"":document.getText().trim();

            if(text.isEmpty()) continue;

            String[] words=text.split("\\s+");
            if(words.length<=WINDOW) {
                out.add(document);
                continue;
            }

            for(int start=0;start<words.length;) {

                int end=Math.min(words.length, start+WINDOW);
                String part=String.join(" ",java.util.Arrays.copyOfRange(words,start,end));

                var metadata=new java.util.HashMap<>(document.getMetadata());
                metadata.put("tokenWindow",start+"-"+end);
                out.add(new Document(part,metadata));

                if(end==words.length) break;

                start=end-OVERLAP;
            }
        }

        return out;
    }
}
