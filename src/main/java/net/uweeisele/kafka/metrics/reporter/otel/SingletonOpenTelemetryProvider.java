package net.uweeisele.kafka.metrics.reporter.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

import java.util.Map;

public class SingletonOpenTelemetryProvider {

    private static volatile OpenTelemetry openTelemetry;
    private static volatile Map<String, String> openTelemetryConfigs;

    public static OpenTelemetry get(Map<String, String> configs) {
        if (openTelemetry == null) {
            synchronized (SingletonOpenTelemetryProvider.class) {
                if (openTelemetry == null) {
                    AutoConfiguredOpenTelemetrySdk openTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
                            .addPropertiesSupplier(() -> configs)
                            .build();
                    openTelemetry = openTelemetrySdk.getOpenTelemetrySdk();
                    openTelemetryConfigs = configs;
                }
            }
        }
        if (!configs.equals(openTelemetryConfigs)) {
            throw new IllegalStateException(String.format("OpenTelemetry has already been initialized with different configs: %s", openTelemetryConfigs));
        }
        return openTelemetry;
    }
}
