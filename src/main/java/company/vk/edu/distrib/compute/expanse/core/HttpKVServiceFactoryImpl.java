package company.vk.edu.distrib.compute.expanse.core;

import company.vk.edu.distrib.compute.KVService;
import company.vk.edu.distrib.compute.KVServiceFactory;
import company.vk.edu.distrib.compute.expanse.context.AppContextUtils;

import java.io.IOException;

public class HttpKVServiceFactoryImpl extends KVServiceFactory {
    @Override
    protected KVService doCreate(int port) throws IOException {
        AuditableKVServiceImpl service = new AuditableKVServiceImpl(port);
        AppContextUtils.addBean(service, AuditableKVServiceImpl.class);
        return service;
    }
}
