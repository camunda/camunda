package io.camunda.zeebe.spring.client.configuration;

import static org.springframework.util.StringUtils.hasText;

import io.camunda.common.auth.Authentication;
import io.camunda.common.auth.DefaultNoopAuthentication;
import io.camunda.common.auth.Product;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CommonConfigurationProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

public class ZeebeClientConfiguration implements io.camunda.zeebe.client.ZeebeClientConfiguration {

  @Autowired private ZeebeClientConfigurationProperties properties;

  @Autowired private CommonConfigurationProperties commonConfigurationProperties;

  @Autowired private Authentication authentication;

  @Lazy // Must be lazy, otherwise we get circular dependencies on beans
  @Autowired
  private JsonMapper jsonMapper;

  @Lazy
  @Autowired(required = false)
  private List<ClientInterceptor> interceptors;

  @Lazy @Autowired private ZeebeClientExecutorService zeebeClientExecutorService;
  private CredentialsProvider credentialsProvider;

  @PostConstruct
  public void applyLegacy() {
    // make sure environment variables and other legacy config options are taken into account
    // (duplicate, also done by  qPostConstruct, whatever)
    properties.applyOverrides();
  }

  @Override
  public String getGatewayAddress() {
    return properties.getGatewayAddress();
  }

  @Override
  public int getGatewayRestApiPort() {
    // TODO: implement
    return 0;
  }

  @Override
  public String getDefaultCommunicationApi() {
    // TODO: implement
    return null;
  }

  @Override
  public boolean useRestApi() {
    // TODO: implement
    return false;
  }

  @Override
  public String getDefaultTenantId() {
    return properties.getDefaultTenantId();
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return properties.getDefaultJobWorkerTenantIds();
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return properties.getNumJobWorkerExecutionThreads();
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return properties.getDefaultJobWorkerMaxJobsActive();
  }

  @Override
  public String getDefaultJobWorkerName() {
    return properties.getDefaultJobWorkerName();
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return properties.getDefaultJobTimeout();
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return properties.getDefaultJobPollInterval();
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return properties.getDefaultMessageTimeToLive();
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return properties.getDefaultRequestTimeout();
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return properties.isPlaintextConnectionEnabled();
  }

  @Override
  public String getCaCertificatePath() {
    return properties.getCaCertificatePath();
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    if (credentialsProvider == null) {
      credentialsProvider = initCredentialsProvider();
    }
    return credentialsProvider;
  }

  @Override
  public Duration getKeepAlive() {
    return properties.getKeepAlive();
  }

  @Override
  public List<ClientInterceptor> getInterceptors() {
    return interceptors;
  }

  @Override
  public JsonMapper getJsonMapper() {
    return jsonMapper;
  }

  @Override
  public String getOverrideAuthority() {
    return properties.getOverrideAuthority();
  }

  @Override
  public int getMaxMessageSize() {
    return properties.getMaxMessageSize();
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.get();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return properties.ownsJobWorkerExecutor();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return properties.getDefaultJobWorkerStreamEnabled();
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return properties.useDefaultRetryPolicy();
  }

  private CredentialsProvider initCredentialsProvider() {
    // TODO: Refactor when integrating Identity SDK
    if (commonConfigurationProperties.getEnabled()
        && !(authentication instanceof DefaultNoopAuthentication)) {
      return new CredentialsProvider() {
        @Override
        public void applyCredentials(final Metadata headers) {
          final Map.Entry<String, String> authHeader = authentication.getTokenHeader(Product.ZEEBE);
          final Metadata.Key<String> authHeaderKey =
              Metadata.Key.of(authHeader.getKey(), Metadata.ASCII_STRING_MARSHALLER);
          headers.put(authHeaderKey, authHeader.getValue());
        }

        @Override
        public boolean shouldRetryRequest(final Throwable throwable) {
          return ((StatusRuntimeException) throwable).getStatus() == Status.DEADLINE_EXCEEDED;
        }
      };
    }
    if (hasText(properties.getCloud().getClientId())
        && hasText(properties.getCloud().getClientSecret())) {
      //        log.debug("Client ID and secret are configured. Creating OAuthCredientialsProvider
      // with: {}", this);
      return CredentialsProvider.newCredentialsProviderBuilder()
          .clientId(properties.getCloud().getClientId())
          .clientSecret(properties.getCloud().getClientSecret())
          .audience(properties.getCloud().getAudience())
          .scope(properties.getCloud().getScope())
          .authorizationServerUrl(properties.getCloud().getAuthUrl())
          .credentialsCachePath(properties.getCloud().getCredentialsCachePath())
          .build();
    }
    if (Environment.system().get("ZEEBE_CLIENT_ID") != null
        && Environment.system().get("ZEEBE_CLIENT_SECRET") != null) {
      // Copied from ZeebeClientBuilderImpl
      final OAuthCredentialsProviderBuilder builder = CredentialsProvider.newCredentialsProviderBuilder();
      final int separatorIndex = properties.getBroker().getGatewayAddress().lastIndexOf(58); // ":"
      if (separatorIndex > 0) {
        builder.audience(properties.getBroker().getGatewayAddress().substring(0, separatorIndex));
      }
      return builder.build();
    }
    return null;
  }

  @Override
  public String toString() {
    return "ZeebeClientConfiguration{"
        + "properties="
        + properties
        + ", commonConfigurationProperties="
        + commonConfigurationProperties
        + ", authentication="
        + authentication
        + ", jsonMapper="
        + jsonMapper
        + ", interceptors="
        + interceptors
        + ", zeebeClientExecutorService="
        + zeebeClientExecutorService
        + '}';
  }
}
