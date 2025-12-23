package dev.horbatiuk.timecapsule;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.ses.SesClient;

@SpringBootTest
class TimecapsuleApplicationTests {

	@Configuration
	static class AwsClientTestConfig {
		@Bean
		@Primary
		public S3Client s3Client() {
			return org.mockito.Mockito.mock(S3Client.class);
		}

		@Bean
		@Primary
		public SchedulerClient schedulerClient() {
			return org.mockito.Mockito.mock(SchedulerClient.class);
		}

		@Bean
		@Primary
		public SesClient sesClient() {
			return org.mockito.Mockito.mock(SesClient.class);
		}
	}

	@Test
	void contextLoads() {
	}

}


