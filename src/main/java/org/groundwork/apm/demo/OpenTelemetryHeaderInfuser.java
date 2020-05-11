package org.groundwork.apm.demo;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class OpenTelemetryHeaderInfuser extends SimpleClientHttpRequestFactory {

    private Tracer tracer;

    OpenTelemetryHeaderInfuser(Tracer tracer) {
        this.tracer = tracer;
    }

    private static HttpTextFormat.Setter<HttpURLConnection> setter =
            new HttpTextFormat.Setter<HttpURLConnection>() {
                @Override
                public void set(HttpURLConnection carrier, String key, String value) {
                    carrier.setRequestProperty(key, value);
                }
            };

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        super.prepareConnection(connection, httpMethod);
        createTracerContext(connection.getURL());
    }

    public void createTracerContext(URL url) throws IOException {
        Span outGoing = tracer.getCurrentSpan(); // tracer.spanBuilder("client").setSpanKind(Span.Kind.CLIENT).startSpan();
        try (Scope scope = tracer.withSpan(outGoing)) {
            // Semantic Convention.
            // (Observe that to set these, Span does not *need* to be the current instance.)
            outGoing.setAttribute("http.method", "GET");
            outGoing.setAttribute("http.url", url.toString());
            HttpURLConnection transportLayer = (HttpURLConnection) url.openConnection();
            // Inject the request with the *current*  Context, which contains our current Span.
            OpenTelemetry.getPropagators().getHttpTextFormat().inject(Context.current(), transportLayer, setter);
        } finally {
            outGoing.end();
        }
    }

}

