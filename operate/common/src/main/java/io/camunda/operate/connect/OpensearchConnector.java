/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.config.operate.OpensearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.config.operate.SslProperties;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.elasticsearch.ElasticsearchException;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;

@Configuration
@Conditional(OpensearchCondition.class)
public class OpensearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchConnector.class);

  private PluginRepository osClientRepository = new PluginRepository();
  private PluginRepository zeebeOsClientRepository = new PluginRepository();
  private final OperateProperties operateProperties;

  private final ObjectMapper objectMapper;

  public OpensearchConnector(
      final OperateProperties operateProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.operateProperties = operateProperties;
    this.objectMapper = objectMapper;
  }

  public void setOsClientRepository(final PluginRepository osClientRepository) {
    this.osClientRepository = osClientRepository;
  }

  public void setZeebeOsClientRepository(final PluginRepository zeebeOsClientRepository) {
    this.zeebeOsClientRepository = zeebeOsClientRepository;
  }

  @Bean
  @Primary
  public OpenSearchClient openSearchClient() {
    osClientRepository.load(operateProperties.getOpensearch().getInterceptorPlugins());
    final OpenSearchClient openSearchClient =
        createOsClient(operateProperties.getOpensearch(), osClientRepository);
    if (operateProperties.getOpensearch().isHealthCheckEnabled()) {
      try {
        final HealthResponse response = openSearchClient.cluster().health();
        LOGGER.info("OpenSearch cluster health: {}", response.status());
      } catch (final IOException e) {
        LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
      }
    } else {
      LOGGER.warn("OpenSearch cluster health check is disabled.");
    }
    return openSearchClient;
  }

  @Bean
  public OpenSearchAsyncClient openSearchAsyncClient() {
    osClientRepository.load(operateProperties.getOpensearch().getInterceptorPlugins());
    final OpenSearchAsyncClient openSearchClient =
        createAsyncOsClient(operateProperties.getOpensearch(), osClientRepository);
    if (operateProperties.getOpensearch().isHealthCheckEnabled()) {
      final CompletableFuture<HealthResponse> healthResponse;
      try {
        healthResponse = openSearchClient.cluster().health();
        healthResponse.whenComplete(
            (response, e) -> {
              if (e != null) {
                LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
              } else {
                LOGGER.info("OpenSearch cluster health: {}", response.status());
              }
            });
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      LOGGER.warn("OpenSearch cluster health check is disabled.");
    }
    return openSearchClient;
  }

  @Bean("zeebeOpensearchClient")
  public OpenSearchClient zeebeOpensearchClient() {
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    zeebeOsClientRepository.load(operateProperties.getZeebeOpensearch().getInterceptorPlugins());
    return createOsClient(operateProperties.getZeebeOpensearch(), zeebeOsClientRepository);
  }

  public OpenSearchAsyncClient createAsyncOsClient(
      final OpensearchProperties osConfig, final PluginRepository osClientRepository) {
    LOGGER.debug("Creating Async OpenSearch connection...");
    LOGGER.debug("Creating OpenSearch connection...");
    if (hasAwsCredentials()) {
      return getAwsAsyncClient(osConfig);
    }
    final HttpHost host = getHttpHost(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(
              httpClientBuilder, osConfig, osClientRepository.asRequestInterceptor());
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, osConfig);
          return requestConfigBuilder;
        });

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
    jsonpMapper.objectMapper().registerModule(new JavaTimeModule());
    builder.setMapper(jsonpMapper);

    final OpenSearchTransport transport = builder.build();
    final OpenSearchAsyncClient openSearchAsyncClient = new OpenSearchAsyncClient(transport);

    if (operateProperties.getOpensearch().isHealthCheckEnabled()) {
      final CompletableFuture<HealthResponse> healthResponse;
      try {
        healthResponse = openSearchAsyncClient.cluster().health();
        healthResponse.whenComplete(
            (response, e) -> {
              if (e != null) {
                LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
              } else {
                LOGGER.info("OpenSearch cluster health: {}", response.status());
              }
            });
      } catch (final IOException e) {
        throw new OperateRuntimeException(e);
      }
      if (!checkHealth(openSearchAsyncClient)) {
        LOGGER.warn("OpenSearch cluster is not accessible");
      } else {
        LOGGER.debug("OpenSearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("OpenSearch cluster health check is disabled.");
    }
    return openSearchAsyncClient;
  }

  private boolean hasAwsCredentials() {
    if (!operateProperties.getOpensearch().isAwsEnabled()) {
      LOGGER.info("AWS Credentials are disabled. Using basic auth.");
      return false;
    }
    final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
    try {
      credentialsProvider.resolveCredentials();
      LOGGER.info("AWS Credentials can be resolved. Use AWS Opensearch");
      return true;
    } catch (final Exception e) {
      LOGGER.warn("AWS not configured due to: {} ", e.getMessage());
      return false;
    }
  }

  public OpenSearchClient createOsClient(
      final OpensearchProperties osConfig, final PluginRepository osClientRepository) {
    LOGGER.debug("Creating OpenSearch connection...");
    if (hasAwsCredentials()) {
      return getAwsClient(osConfig);
    }
    final HttpHost host = getHttpHost(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(
              httpClientBuilder, osConfig, osClientRepository.asRequestInterceptor());
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, osConfig);
          return requestConfigBuilder;
        });

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
    builder.setMapper(jsonpMapper);

    final OpenSearchTransport transport = builder.build();
    final OpenSearchClient openSearchClient = new ExtendedOpenSearchClient(transport);
    if (operateProperties.getOpensearch().isHealthCheckEnabled()) {
      try {
        final HealthResponse response = openSearchClient.cluster().health();
        LOGGER.info("OpenSearch cluster health: {}", response.status());
      } catch (final IOException e) {
        LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
      }

      if (!checkHealth(openSearchClient)) {
        LOGGER.warn("OpenSearch cluster is not accessible");
      } else {
        LOGGER.debug("OpenSearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("OpenSearch cluster health check is disabled.");
    }
    return openSearchClient;
  }

  private OpenSearchClient getAwsClient(final OpensearchProperties osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkHttpClient httpClient = AwsCrtHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            osConfig.getHost(),
            Region.of(region),
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .build());
    return new ExtendedOpenSearchClient(transport);
  }

  private OpenSearchAsyncClient getAwsAsyncClient(final OpensearchProperties osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkHttpClient httpClient = AwsCrtHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            osConfig.getHost(),
            Region.of(region),
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .build());
    return new OpenSearchAsyncClient(transport);
  }

  private HttpHost getHttpHost(final OpensearchProperties osConfig) {
    try {
      final URI uri = new URI(osConfig.getUrl());
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (final URISyntaxException e) {
      throw new OperateRuntimeException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final OpensearchProperties osConfig) {
    if (!StringUtils.hasText(osConfig.getUsername())
        || !StringUtils.hasText(osConfig.getPassword())) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(getHttpHost(osConfig)),
        new UsernamePasswordCredentials(
            osConfig.getUsername(), osConfig.getPassword().toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final SslProperties sslConfig) {
    try {
      final ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      tlsStrategyBuilder.setSslContext(getSSLContext(sslConfig));
      if (!sslConfig.isVerifyHostname()) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      final TlsStrategy tlsStrategy = tlsStrategyBuilder.build();
      final PoolingAsyncClientConnectionManager connectionManager =
          PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);

    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }

  private SSLContext getSSLContext(final SslProperties sslConfig)
      throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    final KeyStore truststore = loadCustomTrustStore(sslConfig);
    final TrustStrategy trustStrategy =
        sslConfig.isSelfSigned() ? new TrustSelfSignedStrategy() : null; // default;
    if (truststore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
    } else {
      // default if custom truststore is empty
      return SSLContext.getDefault();
    }
  }

  private KeyStore loadCustomTrustStore(final SslProperties sslConfig) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      // load custom es server certificate if configured
      final String serverCertificate = sslConfig.getCertificatePath();
      if (serverCertificate != null) {
        setCertificateInTrustStore(trustStore, serverCertificate);
      }
      return trustStore;
    } catch (final Exception e) {
      final String message =
          "Could not create certificate trustStore for the secured OpenSearch Connection!";
      throw new OperateRuntimeException(message, e);
    }
  }

  private void setCertificateInTrustStore(
      final KeyStore trustStore, final String serverCertificate) {
    try {
      final Certificate cert = loadCertificateFromPath(serverCertificate);
      trustStore.setCertificateEntry("opensearch-host", cert);
    } catch (final Exception e) {
      final String message =
          "Could not load configured server certificate for the secured OpenSearch Connection!";
      throw new OperateRuntimeException(message, e);
    }
  }

  private Certificate loadCertificateFromPath(final String certificatePath)
      throws IOException, CertificateException {
    final Certificate cert;
    try (final BufferedInputStream bis =
        new BufferedInputStream(new FileInputStream(certificatePath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        LOGGER.debug("Found certificate: {}", cert);
      } else {
        throw new OperateRuntimeException(
            "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final OpensearchProperties osConfig,
      final HttpRequestInterceptor... requestInterceptors) {
    setupAuthentication(httpAsyncClientBuilder, osConfig);

    LOGGER.trace("Attempt to load interceptor plugins");
    for (final HttpRequestInterceptor interceptor : requestInterceptors) {
      httpAsyncClientBuilder.addRequestInterceptorLast(interceptor);
    }

    if (osConfig.getSsl() != null) {
      setupSSLContext(httpAsyncClientBuilder, osConfig.getSsl());
    }
    return httpAsyncClientBuilder;
  }

  private RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final OpensearchProperties os) {
    if (os.getSocketTimeout() != null) {
      // builder.setSocketTimeout(os.getSocketTimeout());
      builder.setResponseTimeout(Timeout.ofMilliseconds(os.getSocketTimeout()));
    }
    if (os.getConnectTimeout() != null) {
      builder.setConnectTimeout(Timeout.ofMilliseconds(os.getConnectTimeout()));
    }
    return builder;
  }

  public boolean checkHealth(final OpenSearchClient osClient) {
    final OpensearchProperties osConfig = operateProperties.getOpensearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final HealthResponse clusterHealthResponse =
                  osClient.cluster().health(new HealthRequest.Builder().build());
              return clusterHealthResponse.clusterName().equals(osConfig.getClusterName());
            });
  }

  public boolean checkHealth(final OpenSearchAsyncClient osAsyncClient) {
    final OpensearchProperties osConfig = operateProperties.getOpensearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final CompletableFuture<HealthResponse> clusterHealthResponse =
                  osAsyncClient.cluster().health(new HealthRequest.Builder().build());
              clusterHealthResponse.whenComplete(
                  (response, e) -> {
                    if (e != null) {
                      LOGGER.error(String.format("Error checking async health %", e.getMessage()));
                    } else {
                      LOGGER.debug("Succesfully returned checkHealth");
                    }
                  });
              return clusterHealthResponse.get().clusterName().equals(osConfig.getClusterName());
            });
  }

  private RetryPolicy<Boolean> getConnectionRetryPolicy(final OpensearchProperties osConfig) {
    final String logMessage = String.format("connect to OpenSearch at %s", osConfig.getUrl());
    return new RetryPolicy<Boolean>()
        .handle(IOException.class, ElasticsearchException.class)
        .withDelay(Duration.ofSeconds(3))
        .withMaxAttempts(50)
        .onRetry(
            e ->
                LOGGER.info(
                    "Retrying #{} {} due to {}",
                    e.getAttemptCount(),
                    logMessage,
                    e.getLastFailure()))
        .onAbort(e -> LOGGER.error("Abort {} by {}", logMessage, e.getFailure()))
        .onRetriesExceeded(
            e -> LOGGER.error("Retries {} exceeded for {}", e.getAttemptCount(), logMessage));
  }
}
