package company.vk.edu.distrib.compute.expanse.dao.impl;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.expanse.exception.ServerInstantiationException;
import company.vk.edu.distrib.compute.expanse.exception.StorageException;
import company.vk.edu.distrib.compute.expanse.utils.ExceptionUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class FileStorageDao implements Dao<byte[]> {
    public static final String AUDIT_SEPARATOR = "\u0000";
    private static final Logger log = Logger.getLogger(FileStorageDao.class.getName());
    private static final String STORAGE_DIRECTORY = "storage";
    private final AtomicBoolean isOpen;
    private final Lock lock;

    public FileStorageDao() {
        try {
            Path path = Path.of(STORAGE_DIRECTORY);
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            this.isOpen = new AtomicBoolean(true);
            this.lock = new ReentrantLock();

        } catch (Exception e) {
            String message = "Failed to create storage directory.";
            log.severe(message);
            throw new ServerInstantiationException(message, e);
        }
    }

    @Override
    public byte[] get(String key) throws IOException {
        try {
            lock.lock();
            checkOpen();
            checkIsValidStringId(key);
            Path path = Path.of(STORAGE_DIRECTORY, key);

            try (InputStream inputStream = Files.newInputStream(path)) {
                return inputStream.readAllBytes();

            } catch (NoSuchFileException e) {
                throw new NoSuchElementException(e);
            }

        } finally {
            lock.unlock();
        }
    }

    public List<String> getFilesAsStrings(String prefix) {
        try {
            lock.lock();
            checkOpen();
            return Arrays.stream(Objects.requireNonNull(new File(STORAGE_DIRECTORY).listFiles()))
                    .filter(file -> file.getName().startsWith(prefix))
                    .map(file -> {
                        try (FileReader reader = new FileReader(file)) {
                            return reader.readAllAsString();
                        } catch (FileNotFoundException e) {
                            throw new NoSuchElementException(e);
                        } catch (IOException e) {
                            throw new StorageException(e);
                        }
                    }).toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void upsert(String key, byte[] value) throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            checkOpen();
            checkIsValidStringId(key);
            Path path = Path.of(STORAGE_DIRECTORY, key);

            log.fine("Opening file stream to write.");
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                outputStream.write(value);
                log.fine("Successfully wrote file.");
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String id) throws IOException {
        try {
            lock.lock();
            checkOpen();
            checkIsValidStringId(id);

            Path path = Path.of(STORAGE_DIRECTORY, id);
            if (Files.exists(path)) {
                Files.delete(path);
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        this.isOpen.set(false);
    }

    private void checkIsValidStringId(String id) {
        if (Objects.isNull(id) || id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be null");
        }
    }

    private void checkOpen() {
        if (!this.isOpen.get()) {
            throw ExceptionUtils.wrapToInternal(new IllegalStateException("Cannot access closed storage"));
        }
    }
}
