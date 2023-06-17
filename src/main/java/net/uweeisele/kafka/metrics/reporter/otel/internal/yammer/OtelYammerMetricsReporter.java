package net.uweeisele.kafka.metrics.reporter.otel.internal.yammer;

import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.*;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import net.uweeisele.kafka.metrics.reporter.otel.internal.CaseType;
import net.uweeisele.kafka.metrics.reporter.otel.internal.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.DoubleStream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

// https://prometheus.io/docs/practices/naming/
// https://www.robustperception.io/on-the-naming-of-things/
// https://www.robustperception.io/how-does-a-prometheus-summary-work/
// https://opentelemetry.io/docs/reference/specification/metrics/api/
// https://opentelemetry.io/docs/reference/specification/compatibility/prometheus_and_openmetrics/
// https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md#overall-structure
// https://opentelemetry.io/docs/reference/specification/common/attribute-naming/
public class OtelYammerMetricsReporter implements MetricsRegistryListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OtelYammerMetricsReporter.class);

    private final Meter meter;
    private final InstrumentDescriptor.Builder descriptorBuilder;

    private final Object lock = new Object();
    @GuardedBy("lock")
    private final Map<MetricName, List<AutoCloseable>> registeredObservables = new HashMap<>();

    public OtelYammerMetricsReporter(Meter meter, Context context) {
        this.meter = meter;
        this.descriptorBuilder = InstrumentDescriptor.builder(context).withNameManipulator(CaseType.CAMEL_CASE.getManipulator().andThen(CaseType.FIRST_UPPER_CASE.getManipulator()));
    }

    OtelYammerMetricsReporter(Meter meter, InstrumentDescriptor.Builder descriptorBuilder) {
        this.meter = meter;
        this.descriptorBuilder = descriptorBuilder;
    }

    @Override
    public void onMetricAdded(MetricName name, Metric metric) {
        log.trace("Adding instruments for Yammer metrics: {}", name);
        InstrumentDescriptor descriptor = descriptorBuilder.build(name);
        List<AutoCloseable> observables;
        if (metric instanceof Timer) {
            observables = registerTimer(descriptor, (Timer) metric);
        } else if (metric instanceof Metered) {
            observables = registerMeter(descriptor, (Metered) metric);
        } else if (metric instanceof Histogram) {
            observables = registerHistogram(descriptor, (Histogram) metric);
        } else if (metric instanceof Counter) {
            observables = registerCounter(descriptor, (Counter) metric);
        } else if (metric instanceof Gauge<?>) {
            observables = registerGauge(descriptor, (Gauge<?>) metric);
        } else {
            observables = emptyList();
        }

        synchronized (lock) {
            List<AutoCloseable> removedMetrics = registeredObservables.put(name, observables);
            if (removedMetrics != null) {
                log.trace("Replacing instruments of Yammer metric: {}", name);
                removedMetrics.forEach(this::closeObservable);
            } else {
                log.trace("Adding instruments of Yammer metric: {}", name);
            }
        }
    }

    @Override
    public void onMetricRemoved(MetricName name) {
        synchronized (lock) {
            List<AutoCloseable> removedMetrics = registeredObservables.remove(name);
            if (removedMetrics != null) {
                log.trace("Removing instruments of Yammer metric: {}", name);
                removedMetrics.forEach(this::closeObservable);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            for (Iterator<List<AutoCloseable>> it = registeredObservables.values().iterator(); it.hasNext(); ) {
                it.next().forEach(this::closeObservable);
                it.remove();
            }
        }
    }

    private List<AutoCloseable> registerTimer(InstrumentDescriptor descriptor, Timer timer) {
        List<AutoCloseable> observables = new ArrayList<>();
        observables.addAll(registerMeter(descriptor, timer));
        observables.addAll(registerSummarizable(descriptor, timer));
        observables.addAll(registerSampling(descriptor, timer));
        return observables;
    }

    private List<AutoCloseable> registerMeter(InstrumentDescriptor descriptor, Metered metered) {
        InstrumentDescriptor meteredDescriptor = descriptor.withAttribute(b -> b
                        .put("eventType", metered.eventType())
                        .put("rateUnit", metered.rateUnit().name().toLowerCase()));
        return List.of(
                descriptor.withSuffix("count")
                    .register((name, attributes) -> meter
                            .counterBuilder(name)
                            .buildWithCallback(o -> o.record(metered.count(), attributes))),
                meteredDescriptor
                    .register((name, attributes) -> meter
                            .gaugeBuilder(name)
                            .buildWithCallback(o -> o.record(metered.meanRate(), attributes))),
                meteredDescriptor.withAttribute(b -> b.put("rateWindow", Duration.ofMinutes(1).toString()))
                    .register((name, attributes) -> meter
                            .gaugeBuilder(name)
                            .buildWithCallback(o -> o.record(metered.oneMinuteRate(), attributes))),
                meteredDescriptor.withAttribute(b -> b.put("rateWindow", Duration.ofMinutes(5).toString()))
                    .register((name, attributes) -> meter
                            .gaugeBuilder(name)
                            .buildWithCallback(o -> o.record(metered.fiveMinuteRate(), attributes))),
                meteredDescriptor.withAttribute(b -> b.put("rateWindow", Duration.ofMinutes(15).toString()))
                    .register((name, attributes) -> meter
                            .gaugeBuilder(name)
                            .buildWithCallback(o -> o.record(metered.fifteenMinuteRate(), attributes)))
        );
    }

    private List<AutoCloseable> registerHistogram(InstrumentDescriptor descriptor, Histogram histogram) {
        List<AutoCloseable> observables = new ArrayList<>();
        observables.add(
                descriptor.withSuffix("count")
                        .register((name, attributes) -> meter
                                .counterBuilder(name)
                                .buildWithCallback(o -> o.record(histogram.count(), attributes)))
        );
        observables.addAll(registerSummarizable(descriptor, histogram));
        observables.addAll(registerSampling(descriptor, histogram));
        return observables;
    }

    private List<AutoCloseable> registerSummarizable(InstrumentDescriptor descriptor, Summarizable summarizable) {
        return List.of(
                descriptor.withSuffix("max")
                        .register((name, attributes) -> meter
                                .gaugeBuilder(name)
                                .buildWithCallback(o -> o.record(summarizable.max(), attributes))),
                descriptor.withSuffix("min")
                        .register((name, attributes) -> meter
                                .gaugeBuilder(name)
                                .buildWithCallback(o -> o.record(summarizable.min(), attributes))),
                descriptor.withSuffix("mean")
                        .register((name, attributes) -> meter
                                .gaugeBuilder(name)
                                .buildWithCallback(o -> o.record(summarizable.mean(), attributes))),
                descriptor.withSuffix("sum")
                        .register((name, attributes) -> meter
                                .gaugeBuilder(name)
                                .buildWithCallback(o -> o.record(summarizable.sum(), attributes))),
                descriptor.withSuffix("stdDev")
                        .register((name, attributes) -> meter
                                .gaugeBuilder(name)
                                .buildWithCallback(o -> o.record(summarizable.stdDev(), attributes)))
                );
    }

    private List<AutoCloseable> registerSampling(InstrumentDescriptor descriptor, Sampling sampling) {
        return DoubleStream.of(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                .mapToObj(percentile -> descriptor.withAttribute(b -> b.put("percentile", percentile))
                        .register((name, attributes) -> meter
                                .gaugeBuilder(name)
                                .buildWithCallback(o -> o.record(sampling.getSnapshot().getValue(percentile), attributes))))
                .collect(toList());
    }

    private List<AutoCloseable> registerCounter(InstrumentDescriptor descriptor, Counter counter) {
        return List.of(
                descriptor.withSuffix("count")
                        .register((name, attributes) -> meter
                                .counterBuilder(name)
                                .buildWithCallback(o -> o.record(counter.count(), attributes)))
        );
    }

    private List<AutoCloseable> registerGauge(InstrumentDescriptor descriptor, Gauge<?> gauge) {
        if (gauge.value() == null) {
            log.trace("Cannot map yammer gauge metric {} to instrument, because its value is 'null'.", descriptor);
            return emptyList();
        } else if (!(gauge.value() instanceof Number)) {
            log.trace("Cannot map yammer gauge metric {} to instrument, because it is of type {}. Only numeric types are supported.",
                    descriptor, gauge.value().getClass().getSimpleName());
            return emptyList();
        } else {
            return List.of(
                    descriptor
                            .register((name, attributes) -> meter
                                    .gaugeBuilder(name)
                                    .buildWithCallback(o -> o.record(toDouble(gauge.value()), attributes)))
            );
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private void closeObservable(AutoCloseable observable) {
        try {
            observable.close();
        } catch (Exception e) {
            log.warn("Error occurred closing observable {}", observable, e);
        }
    }

}
