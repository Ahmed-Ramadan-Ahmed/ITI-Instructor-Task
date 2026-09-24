package org.hrcopilot.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;

@Component
public class RequestSizeFilter extends OncePerRequestFilter {
    private static final long JSON_LIMIT=1024L*1024, MULTIPART_LIMIT=20L*1024*1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long limit =
                request.getContentType()!=null
                && request.getContentType().toLowerCase().startsWith("multipart/")
                ? MULTIPART_LIMIT : JSON_LIMIT;

        if(request.getContentLengthLong()>limit){
            response.sendError(413,"Request exceeds the configured size limit");
            return;
        }

        // Leave multipart parsing to the servlet container; eagerly consuming its stream prevents getParts() from working.
        if(request.getContentType()!=null && request.getContentType().toLowerCase().startsWith("multipart/")){
            chain.doFilter(request,response);
            return;
        }

        byte[] body=request.getInputStream().readNBytes((int)limit+1);

        if(body.length>limit){
            response.sendError(413,"Request exceeds the configured size limit");
            return;
        }

        chain.doFilter(
                new BoundedBodyRequest(request,body),
                response
        );
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BoundedBodyRequest(HttpServletRequest request,byte[] body){
            super(request);
            this.body=body;
        }

        @Override public int getContentLength() {
            return body.length;
        }

        @Override public long getContentLengthLong() {
            return body.length;
        }

        @Override public ServletInputStream getInputStream(){
            var in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() {
                    return in.read();
                }
                @Override public boolean isFinished() {
                    return in.available()==0;
                }
                @Override public boolean isReady() {
                    return true;
                }
                @Override public void setReadListener(ReadListener listener) {
                    try{
                        if(isFinished())
                            listener.onAllDataRead();
                        else
                            listener.onDataAvailable();
                    }
                    catch(IOException e) {
                        listener.onError(e);
                    }
                }
            };
        }
        @Override public BufferedReader getReader() throws IOException {
            return new BufferedReader(
                    new InputStreamReader(
                            getInputStream(),
                            getCharacterEncoding()==null?"UTF-8":getCharacterEncoding()
                    )
            );
        }
    }
}
