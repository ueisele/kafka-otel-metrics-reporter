/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package net.uweeisele.kafka.metrics.reporter.otel;

import kafka.server.KafkaConfig$;
import kafka.test.ClusterConfig;
import kafka.test.ClusterInstance;
import kafka.test.annotation.ClusterTest;
import kafka.test.annotation.ClusterTestDefaults;
import kafka.test.annotation.Type;
import kafka.test.junit.ClusterTestExtensions;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

@ClusterTestDefaults(clusterType = Type.CO_KRAFT)
@ExtendWith(ClusterTestExtensions.class)
class OtelMetricsReporterKafkaClientIntTest {

    @BeforeEach
    void initClusterConfigs(ClusterConfig clusterConfig) {
        clusterConfig.serverProperties().put(KafkaConfig$.MODULE$.AutoCreateTopicsEnableProp(), false);
        clusterConfig.serverProperties().put(KafkaConfig$.MODULE$.GroupInitialRebalanceDelayMsProp(), 0);
        clusterConfig.serverProperties().put(KafkaConfig$.MODULE$.OffsetsTopicReplicationFactorProp(), (short) 1);
        clusterConfig.serverProperties().put(KafkaConfig$.MODULE$.OffsetsTopicPartitionsProp(), 1);
        clusterConfig.serverProperties().put(KafkaConfig$.MODULE$.MetricReporterClassesProp(), OtelMetricsReporter.class.getName());
        clusterConfig.serverProperties().put("otel.service.name" , "kafka-broker");
        clusterConfig.serverProperties().put("otel.traces.exporter" , "none");
        clusterConfig.serverProperties().put("otel.metrics.exporter" , "prometheus");
        clusterConfig.serverProperties().put("otel.exporter.prometheus.port" , "9464");
    }

    @ClusterTest
    void someLibraryMethodReturnsTrue(ClusterInstance clusterInstance) {
        try(Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, clusterInstance.bootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("test", 1, (short)1)));
        }

        Map<String, Object> producerConfigs = Map.ofEntries(
                Map.entry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, clusterInstance.bootstrapServers()),
                Map.entry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class),
                Map.entry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class),
                //Map.entry(ProducerConfig.CLIENT_ID_CONFIG, "producer-1"),
                //Map.entry(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, OtelMetricsReporter.class.getName()),
                Map.entry("otel.traces.exporter" , "none"),
                Map.entry("otel.metrics.exporter" , "prometheus"),
                Map.entry("otel.exporter.prometheus.port" , "9464")
        );
        KafkaProducer<String, String> producer1 = new KafkaProducer<>(producerConfigs);
        producer1.send(new ProducerRecord<>("test", "key", "value"));

        //System.out.println("### Kafka Bootstrapserver: " + clusterInstance.bootstrapServers());
        //try {
        //    Thread.sleep(5000000);
        //} catch (InterruptedException e) {
        //    throw new RuntimeException(e);
        //}

        producer1.close();
    }

}
