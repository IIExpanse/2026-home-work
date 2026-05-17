package company.vk.edu.distrib.compute.expanse.core;

import company.vk.edu.distrib.compute.AuditService;
import company.vk.edu.distrib.compute.AuditServiceFactory;

import java.io.IOException;

public class AuditServiceFactoryImpl extends AuditServiceFactory {
    @Override
    protected AuditService doCreate(String bootstrapServers, String consumerGroupId) throws IOException {
        AuditServiceImpl auditService = new AuditServiceImpl();
        auditService.setBootstrapServers(bootstrapServers, consumerGroupId);
        return auditService;
    }
}
