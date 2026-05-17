package company.vk.edu.distrib.compute.expanse.core;

import company.vk.edu.distrib.compute.AuditEvent;
import company.vk.edu.distrib.compute.AuditService;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.expanse.dao.impl.FileStorageDao;
import company.vk.edu.distrib.compute.expanse.exception.StorageException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static company.vk.edu.distrib.compute.expanse.dao.impl.FileStorageDao.AUDIT_SEPARATOR;

public class AuditServiceImpl implements AuditService {
    public static final String AUDIT_TOPIC = "audit";
    private static final Logger log = Logger.getLogger(AuditServiceImpl.class.getName());
    private static final String DEFAULT_CONSUMER_GROUP = "audit-consumer";
    private String consumerGroup = DEFAULT_CONSUMER_GROUP;
    private final Lock lock;
    private final String clientId;
    private final AtomicBoolean isRunning;
    private Properties props;
    private Thread thread;
    private static int instanceCount;

    public AuditServiceImpl() {
        this.lock = new ReentrantLock();
        this.props = new Properties();
        this.clientId = "audit-reader-" + instanceCount++;
        this.isRunning = new AtomicBoolean(false);
    }

    @Override
    public void start() {
        this.start(consumerGroup);
    }

    public void start(String consumerGroup) {
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        this.consumerGroup = consumerGroup;

        try {
            lock.lock();
            if (!isRunning.get()) {
                return;
            }
            isRunning.set(true);
            thread = new Thread(() -> {
                try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {

                    consumer.subscribe(Collections.singletonList(AUDIT_TOPIC));

                    while (isRunning.get()) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10));
                        try (Dao<byte[]> dao = new FileStorageDao()) {
                            for (ConsumerRecord<String, String> consumerRecord : records) {
                                log.info("Received record with offset " + consumerRecord.offset());

                                dao.upsert(getPrefix() + consumerRecord.offset(), consumerRecord.value().getBytes());
                            }                            if (log.isLoggable(Level.INFO)) {
                                log.info("Consumed " + records.count() + " records");
                            }
                        }

                    }
                } catch (Exception e) {
                    log.severe("Error while starting audit reader: " + e.getMessage());
                }
            });
            thread.start();

        } finally {
            lock.unlock();
        }
        log.info("Started audit reader");
    }

    @Override
    public void stop() {
        try {
            lock.lock();
            log.info("Stopping audit reader");
            if (isRunning.get()) {
                isRunning.set(false);
                thread.join();
            }

        } catch (InterruptedException e) {
            log.severe("Error while stopping audit reader: " + e.getMessage());
            Thread.currentThread().interrupt();

        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<AuditEvent> listAuditEntries() {
        if (!isRunning.get()) {
            return Collections.emptyList();
        }
        try (FileStorageDao dao = new FileStorageDao()) {
            return dao.getFilesAsStrings(getPrefix()).stream()
                    .map(s -> {
                        String[] parts = s.split(AUDIT_SEPARATOR);
                        if (parts.length != 3) {
                            throw new IllegalArgumentException("Invalid audit entry: " + s);
                        }
                        return new AuditEvent(parts[0], parts[1], Long.parseLong(parts[2]));
                    }).toList();

        } catch (IOException e) {
            throw new StorageException(e);
        }
    }

    public void setBootstrapServers(String bootstrapServers, String consumerGroup) {
        Properties properties = new Properties();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        if (consumerGroup != null) {
            this.consumerGroup = consumerGroup;
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        }
        this.props = properties;
    }

    private String getPrefix() {
        return AUDIT_TOPIC + "-" + consumerGroup + "-" + clientId + "-";
    }
}
