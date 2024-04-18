package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationSpringImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.grpc.ClientInterceptor;
import java.util.List;
import java.util.Map.Entry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

public class ZeebeClientConfigurationTest {
  private static ZeebeClientConfiguration configuration(
      final ZeebeClientConfigurationProperties legacyProperties,
      final CamundaClientProperties properties,
      final Authentication authentication,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final ZeebeClientExecutorService executorService) {
    return new ZeebeClientConfigurationSpringImpl(
        legacyProperties, properties, authentication, jsonMapper, interceptors, executorService);
  }

  private static ZeebeClientConfigurationProperties legacyProperties() {
    return new ZeebeClientConfigurationProperties(new MockEnvironment());
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  private static Authentication authentication() {
    return new Authentication() {
      @Override
      public Entry<String, String> getTokenHeader(final Product product) {
        return null;
      }

      @Override
      public void resetToken(final Product product) {}
    };
  }

  private static JsonMapper jsonMapper() {
    return new ZeebeObjectMapper();
  }

  private static ZeebeClientExecutorService executorService() {
    return ZeebeClientExecutorService.createDefault();
  }

  @Test
  void shouldCreateSingletonCredentialProvider() {
    final ZeebeClientConfiguration configuration =
        configuration(
            legacyProperties(),
            properties(),
            authentication(),
            jsonMapper(),
            List.of(),
            executorService());
    final CredentialsProvider credentialsProvider1 = configuration.getCredentialsProvider();
    final CredentialsProvider credentialsProvider2 = configuration.getCredentialsProvider();
    assertThat(credentialsProvider1).isSameAs(credentialsProvider2);
  }
}
