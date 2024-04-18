package io.camunda.zeebe.spring.client.config;

import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientAllAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationSpringImpl.IdentityCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {ZeebeClientAllAutoConfiguration.class, ZeebeClientProdAutoConfiguration.class},
    properties = {
      "camunda.client.mode=oidc",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
public class ZeebeClientConfigurationOidcTest {
  @Autowired ZeebeClientConfiguration zeebeClientConfiguration;
  @Autowired JsonMapper jsonMapper;
  @Autowired ZeebeClientExecutorService zeebeClientExecutorService;

  @Test
  void shouldContainsZeebeClientConfiguration() {
    assertThat(zeebeClientConfiguration).isNotNull();
  }

  @Test
  void shouldNotHaveCredentialsProvider() {
    assertThat(zeebeClientConfiguration.getCredentialsProvider())
        .isInstanceOf(IdentityCredentialsProvider.class);
  }

  @Test
  void shouldHaveGatewayAddress() {
    assertThat(zeebeClientConfiguration.getGatewayAddress()).isEqualTo("localhost:26500");
  }

  @Test
  void shouldHaveDefaultTenantId() {
    assertThat(zeebeClientConfiguration.getDefaultTenantId())
        .isEqualTo(DEFAULT.getDefaultTenantId());
  }

  @Test
  void shouldHaveDefaultJobWorkerTenantIds() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerTenantIds())
        .isEqualTo(DEFAULT.getDefaultJobWorkerTenantIds());
  }

  @Test
  void shouldHaveNumJobWorkerExecutionThreads() {
    assertThat(zeebeClientConfiguration.getNumJobWorkerExecutionThreads())
        .isEqualTo(DEFAULT.getNumJobWorkerExecutionThreads());
  }

  @Test
  void shouldHaveDefaultJobWorkerMaxJobsActive() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerMaxJobsActive())
        .isEqualTo(DEFAULT.getDefaultJobWorkerMaxJobsActive());
  }

  @Test
  void shouldHaveDefaultJobWorkerName() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerName())
        .isEqualTo(DEFAULT.getDefaultJobWorkerName());
  }

  @Test
  void shouldHaveDefaultJobTimeout() {
    assertThat(zeebeClientConfiguration.getDefaultJobTimeout())
        .isEqualTo(DEFAULT.getDefaultJobTimeout());
  }

  @Test
  void shouldHaveDefaultJobPollInterval() {
    assertThat(zeebeClientConfiguration.getDefaultJobPollInterval())
        .isEqualTo(DEFAULT.getDefaultJobPollInterval());
  }

  @Test
  void shouldHaveDefaultMessageTimeToLive() {
    assertThat(zeebeClientConfiguration.getDefaultMessageTimeToLive())
        .isEqualTo(DEFAULT.getDefaultMessageTimeToLive());
  }

  @Test
  void shouldHaveDefaultRequestTimeout() {
    assertThat(zeebeClientConfiguration.getDefaultRequestTimeout())
        .isEqualTo(DEFAULT.getDefaultRequestTimeout());
  }

  @Test
  void shouldHavePlaintextConnectionEnabled() {
    assertThat(zeebeClientConfiguration.isPlaintextConnectionEnabled()).isEqualTo(true);
  }

  @Test
  void shouldHaveCaCertificatePath() {
    assertThat(zeebeClientConfiguration.getCaCertificatePath())
        .isEqualTo(DEFAULT.getCaCertificatePath());
  }

  @Test
  void shouldHaveKeepAlive() {
    assertThat(zeebeClientConfiguration.getKeepAlive()).isEqualTo(DEFAULT.getKeepAlive());
  }

  @Test
  void shouldNotHaveClientInterceptors() {
    assertThat(zeebeClientConfiguration.getInterceptors()).isEmpty();
  }

  @Test
  void shouldHaveJsonMapper() {
    assertThat(zeebeClientConfiguration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldHaveOverrideAuthority() {
    assertThat(zeebeClientConfiguration.getOverrideAuthority())
        .isEqualTo(DEFAULT.getOverrideAuthority());
  }

  @Test
  void shouldHaveMaxMessageSize() {
    assertThat(zeebeClientConfiguration.getMaxMessageSize()).isEqualTo(DEFAULT.getMaxMessageSize());
  }

  @Test
  void shouldHaveJobWorkerExecutor() {
    assertThat(zeebeClientConfiguration.jobWorkerExecutor())
        .isEqualTo(zeebeClientExecutorService.get());
  }

  @Test
  void shouldHaveOwnsJobWorkerExecutor() {
    assertThat(zeebeClientConfiguration.ownsJobWorkerExecutor()).isEqualTo(true);
  }

  @Test
  void shouldHaveDefaultJobWorkerStreamEnabled() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerStreamEnabled())
        .isEqualTo(DEFAULT.getDefaultJobWorkerStreamEnabled());
  }

  @Test
  void shouldHaveDefaultRetryPolicy() {
    assertThat(zeebeClientConfiguration.useDefaultRetryPolicy())
        .isEqualTo(DEFAULT.useDefaultRetryPolicy());
  }
}
