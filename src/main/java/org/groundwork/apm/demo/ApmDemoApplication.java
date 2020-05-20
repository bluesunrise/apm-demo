package org.groundwork.apm.demo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.export.BatchSpansProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ApmDemoApplication extends SimpleClientHttpRequestFactory implements CommandLineRunner {

//	private final RestTemplate restTemplate;Q
//
//	public ApmDemoApplication(RestTemplateBuilder restTemplateBuilder) {
//		this.restTemplate = restTemplateBuilder.build();
//	}

	public static void main(String[] args) {
		SpringApplication.run(ApmDemoApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		Tracer tracer = OpenTelemetry.getTracerProvider().get("groundwork-instrumentation","semver:1.0.0");
		LoggingSpanExporter loggingExporter = new LoggingSpanExporter();
		TracerSdkProvider tracerProvider = OpenTelemetrySdk.getTracerProvider();
		tracerProvider.addSpanProcessor(SimpleSpansProcessor.create(loggingExporter));
		setupJaegerExporter();

		while (true) {
			Span span = tracer.spanBuilder("groundwork-apm-demo").setSpanKind(Span.Kind.CLIENT).startSpan();
			try (Scope scope = tracer.withSpan(span)) {
				// your use case
				Thread.sleep(2000);
				String url = "http://localhost:8080/demo/route1";
				RestTemplate template = new RestTemplate(new OpenTelemetryHeaderInfuser(tracer));
				String result = template.getForObject(url, String.class);
				System.out.println(result);

				url = "http://localhost:8080/demo/route2";
				result = template.getForObject(url, String.class);
				System.out.println(result);

			} catch (Throwable t) {
				Status status = Status.UNKNOWN.withDescription("Change it to your error message");
				span.setStatus(status);
			} finally {
				span.end(); // closing the scope does not end the span, this has to be done manually
				System.out.println(span.getContext().getTraceId());
			}
		}
	}

	private void setupJaegerExporter() {
		// Create a channel towards Jaeger end point
		ManagedChannel jaegerChannel =
				ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build();
		// Export traces to Jaeger
		JaegerGrpcSpanExporter jaegerExporter =
				JaegerGrpcSpanExporter.newBuilder()
						.setServiceName("demoExporter")
						.setChannel(jaegerChannel)
						.setDeadlineMs(30000)
						.build();

		// Set to process the spans by the Jaeger Exporter
		OpenTelemetrySdk.getTracerProvider().addSpanProcessor(SimpleSpansProcessor.create(jaegerExporter));
	}
}

