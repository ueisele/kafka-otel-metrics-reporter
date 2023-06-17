package net.uweeisele.kafka.metrics.reporter.otel.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import org.apache.kafka.common.metrics.MetricsContext;

import java.util.Map;

public class Context {

    private String namespace = "";

    private Attributes attributes = Attributes.empty();

    public String getNamespace() {
        return namespace;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public Context setMetricsContext(MetricsContext metricsContext) {
        this.namespace = metricsContext.contextLabels().get(MetricsContext.NAMESPACE);
        AttributesBuilder attributesBuilder = Attributes.builder();
        metricsContext.contextLabels().entrySet().stream()
                .filter(e -> !e.getKey().equals(MetricsContext.NAMESPACE))
                .map(e -> Map.entry(CaseType.CAMEL_CASE.getManipulator().apply(e.getKey().replace("kafka.", "")), e.getValue()))
                .forEach(e -> attributesBuilder.put(e.getKey(), e.getValue()));
        attributes = attributesBuilder.build();
        return this;
    }
}
