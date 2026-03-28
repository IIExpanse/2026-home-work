package company.vk.edu.distrib.compute;

//import company.vk.edu.distrib.compute.nihuaway00.NihuawayKVService;
//import company.vk.edu.distrib.compute.nihuaway00.NihuawayKVServiceFactory;

import module java.base;
import company.vk.edu.distrib.compute.dummy.DummyKVServiceFactory;
import org.slf4j.LoggerFactory;

public class Server {
    //    public static void main(String[] args) throws IOException {
    //        var log = LoggerFactory.getLogger("server");
    //        var port = 8080;
    //        KVService storage = new NihuawayKVServiceFactory().create(port);
    //        storage.start();
    //        log.info("Server started on port {}", port);
    //        Runtime.getRuntime().addShutdownHook(new Thread(storage::stop));
    //
    //        try {
    //            Thread.currentThread().join();
    //        } catch (InterruptedException e) {
    //            log.warn("error", e);
    //        }
    //    }

    void main() throws IOException {
        var log = LoggerFactory.getLogger("server");
        var port = 8080;
        KVService storage = new DummyKVServiceFactory().create(port);
        storage.start();
        log.info("Server started on port {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(storage::stop));
    }
}
