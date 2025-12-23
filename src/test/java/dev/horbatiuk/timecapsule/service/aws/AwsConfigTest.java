package dev.horbatiuk.timecapsule.service.aws;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.scheduler.SchedulerClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AwsConfigPlainTest {

    @Test
    void testAwsBeansCreation() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {

            context.getEnvironment().getSystemProperties().put("aws.access-key", "test-access-key");
            context.getEnvironment().getSystemProperties().put("aws.secret-key", "test-secret-key");
            context.getEnvironment().getSystemProperties().put("aws.region", "us-east-1");

            context.register(AwsConfig.class);
            context.refresh();

            S3Client s3Client = context.getBean(S3Client.class);
            SchedulerClient schedulerClient = context.getBean(SchedulerClient.class);

            assertNotNull(s3Client);
            assertNotNull(schedulerClient);
        }
    }
}
