package dev.horbatiuk.timecapsule.service.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.scheduler.SchedulerClient;

@Configuration
public class AwsConfig {

    private static final Logger logger = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.secret-key}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Bean
    public SchedulerClient schedulerClient() {
        logger.info("Initializing AWS SchedulerClient in region: {}", region);
        SchedulerClient client = SchedulerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
        logger.debug("AWS SchedulerClient initialized");
        return client;
    }

    @Bean
    public S3Client s3Client() {
        logger.info("Initializing AWS S3Client in region: {}", region);
        S3Client client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
        logger.debug("AWS S3Client initialized");
        return client;
    }
}
