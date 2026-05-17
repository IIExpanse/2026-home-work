package company.vk.edu.distrib.compute.expanse.core;

import company.vk.edu.distrib.compute.AuditableKVService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuditableKVServiceImpl extends HttpKVService implements AuditableKVService {
    private static final Logger log = Logger.getLogger(AuditableKVServiceImpl.class.getName());
    private KafkaProducer<String, String> producer;
    private Properties props;
    private boolean async;

    public AuditableKVServiceImpl(int port) throws IOException {
        super(port);
        this.props = new Properties();
    }

    @Override
    public void setBootstrapServers(String bootstrapServers) {
        Properties properties = new Properties();

        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.props = properties;
        if (producer != null) {
            producer.close();
        }
        this.producer = new KafkaProducer<>(props);
    }

    @Override
    public void setAsync(boolean enabled) {
        this.async = enabled;
    }

    @Override
    public void stop() {
        super.stop();
        if (producer != null) {
            log.info("Closing producer");
            producer.close();
        }
    }

    public void send(String topic, String message) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, null, message);
        Future<?> future = producer.send(producerRecord, (metadata, exception) -> {
            if (exception == null) {
                log.info("Message sent to topic: " + metadata.topic());
            } else {
                if (exception.getMessage() != null) {
                    log.severe("Error sending message to topic: " + exception.getMessage());
                }
            }
        });
        if (!async) {
            try {
                future.get();
            } catch (ExecutionException | InterruptedException e) {
                log.severe("Error sending message to topic: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        if (log.isLoggable(Level.INFO)) {
            log.info("Message sent to topic: %s".formatted(topic));
        }
    }
}
