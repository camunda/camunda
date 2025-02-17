/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.OpenSearchProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.util.VisibleForTesting;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
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
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;

@Configuration
@Conditional(OpenSearchCondition.class)
public class OpenSearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchConnector.class);
  private static final String AWS_OPENSEARCH_SERVICE_NAME = "es";

  private PluginRepository osClientRepository = new PluginRepository();
  private PluginRepository zeebeOsClientRepository = new PluginRepository();
  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper tasklistObjectMapper;

  @VisibleForTesting
  public void setOsClientRepository(final PluginRepository osClientRepository) {
    this.osClientRepository = osClientRepository;
  }

  @VisibleForTesting
  public void setZeebeOsClientRepository(final PluginRepository zeebeOsClientRepository) {
    this.zeebeOsClientRepository = zeebeOsClientRepository;
  }

  @VisibleForTesting
  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  @VisibleForTesting
  public void setTasklistObjectMapper(final ObjectMapper tasklistObjectMapper) {
    this.tasklistObjectMapper = tasklistObjectMapper;
  }

  @Bean
  public OpenSearchClient tasklistOsClient() {
    osClientRepository.load(tasklistProperties.getOpenSearch().getInterceptorPlugins());
    final OpenSearchClient openSearchClient =
        createOsClient(tasklistProperties.getOpenSearch(), osClientRepository);
    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (final IOException e) {
      LOGGER.error(
          "Error in getting health status from localhost:"
              + tasklistProperties.getOpenSearch().getPort(),
          e);
    }
    return openSearchClient;
  }

  @Bean
  public OpenSearchClient tasklistZeebeOsClient() {
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    zeebeOsClientRepository.load(tasklistProperties.getZeebeOpenSearch().getInterceptorPlugins());
    return createOsClient(tasklistProperties.getZeebeOpenSearch(), zeebeOsClientRepository);
  }

  @Bean
  public RestClient tasklistOsRestClient() {
    osClientRepository.load(tasklistProperties.getOpenSearch().getInterceptorPlugins());
    final var httpHost = getHttpHost(tasklistProperties.getOpenSearch());
    return RestClient.builder(httpHost)
        .setHttpClientConfigCallback(
            b ->
                configureApacheHttpClient(
                    b,
                    tasklistProperties.getOpenSearch(),
                    osClientRepository.asRequestInterceptor()))
        .build();
  }

  @Bean
  public OpenSearchAsyncClient tasklistOsAsyncClient() {
    osClientRepository.load(tasklistProperties.getOpenSearch().getInterceptorPlugins());
    final OpenSearchAsyncClient openSearchClient =
        createAsyncOsClient(tasklistProperties.getOpenSearch(), osClientRepository);
    final CompletableFuture<HealthResponse> healthResponse;
    try {
      healthResponse = openSearchClient.cluster().health();
      healthResponse.whenComplete(
          (response, e) -> {
            if (e != null) {
              LOGGER.error(
                  "Error in getting health status from localhost:"
                      + tasklistProperties.getOpenSearch().getPort(),
                  e);
            } else {
              LOGGER.info("OpenSearch cluster health: {}", response.status());
            }
          });
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return openSearchClient;
  }

  private OpenSearchAsyncClient createAsyncOsClient(
      final OpenSearchProperties osConfig, final PluginRepository pluginRepository) {
    LOGGER.debug("Creating Async OpenSearch connection...");
    LOGGER.debug("Creating OpenSearch connection...");
    if (hasAwsCredentials()) {
      return getAwsAsyncClient(osConfig);
    }
    final HttpHost host = getHttpHostForClient5(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureApacheHttpClient5(
              httpClientBuilder, osConfig, pluginRepository.asRequestInterceptor());
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

    final CompletableFuture<HealthResponse> healthResponse;
    try {
      healthResponse = openSearchAsyncClient.cluster().health();
      healthResponse.whenComplete(
          (response, e) -> {
            if (e != null) {
              LOGGER.error(
                  "Error in getting health status from localhost:"
                      + tasklistProperties.getOpenSearch().getPort(),
                  e);
            } else {
              LOGGER.info("OpenSearch cluster health: {}", response.status());
            }
          });
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }

    if (!checkHealth(openSearchAsyncClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("OpenSearch connection was successfully created.");
    }
    return openSearchAsyncClient;
  }

  protected OpenSearchClient createOsClient(
      final OpenSearchProperties osConfig, final PluginRepository pluginRepository) {
    LOGGER.debug("Creating OpenSearch connection...");
    if (hasAwsCredentials()) {
      return getAwsClient(osConfig);
    }
    final HttpHost host = getHttpHostForClient5(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureApacheHttpClient5(
              httpClientBuilder, osConfig, pluginRepository.asRequestInterceptor());
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, osConfig);
          return requestConfigBuilder;
        });

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(tasklistObjectMapper);
    builder.setMapper(jsonpMapper);

    final OpenSearchTransport transport = builder.build();
    final OpenSearchClient openSearchClient = new OpenSearchClient(transport);
    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (final IOException e) {
      LOGGER.error(
          "Error in getting health status from localhost:"
              + tasklistProperties.getOpenSearch().getPort(),
          e);
    }

    if (!checkHealth(openSearchClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("OpenSearch connection was successfully created.");
    }
    return openSearchClient;
  }

  private HttpHost getHttpHostForClient5(final OpenSearchProperties osConfig) {
    final URI uri = getOsUri(osConfig);
    return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
  }

  private org.apache.http.HttpHost getHttpHost(final OpenSearchProperties osConfig) {
    final URI uri = getOsUri(osConfig);
    return new org.apache.http.HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  private URI getOsUri(final OpenSearchProperties osConfig) {
    try {
      return new URI(osConfig.getUrl());
    } catch (final URISyntaxException e) {
      throw new TasklistRuntimeException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final OpenSearchProperties osConfig) {
    if (!useBasicAuthentication(osConfig)) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(getHttpHostForClient5(osConfig)),
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
      throw new TasklistRuntimeException(message, e);
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
      throw new TasklistRuntimeException(message, e);
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
        throw new TasklistRuntimeException(
            "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }

  private HttpAsyncClientBuilder configureApacheHttpClient5(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final OpenSearchProperties osConfig,
      final org.apache.hc.core5.http.HttpRequestInterceptor... httpRequestInterceptors) {
    setupAuthentication(httpAsyncClientBuilder, osConfig);

    LOGGER.trace("Attempt to load interceptor plugins");
    for (final var interceptor : httpRequestInterceptors) {
      httpAsyncClientBuilder.addRequestInterceptorFirst(interceptor);
    }

    if (osConfig.getSsl() != null) {
      setupSSLContext(httpAsyncClientBuilder, osConfig.getSsl());
    }
    return httpAsyncClientBuilder;
  }

  private RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final OpenSearchProperties os) {
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
    final OpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    if (!osConfig.isHealthCheckEnabled()) {
      LOGGER.debug("Opensearch health check is disabled");
      return true;
    }
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
    final OpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    if (!osConfig.isHealthCheckEnabled()) {
      LOGGER.debug("Opensearch health check is disabled");
      return true;
    }
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

  private RetryPolicy<Boolean> getConnectionRetryPolicy(final OpenSearchProperties osConfig) {
    final String logMessage = String.format("connect to OpenSearch at %s", osConfig.getUrl());
    return new RetryPolicy<Boolean>()
        .handle(IOException.class, OpenSearchException.class)
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

  public boolean hasAwsCredentials() {
    if (!tasklistProperties.getOpenSearch().isAwsEnabled()) {
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

  private OpenSearchAsyncClient getAwsAsyncClient(final OpenSearchProperties osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkHttpClient httpClient = AwsCrtHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            osConfig.getHost(),
            Region.of(region),
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(tasklistObjectMapper))
                .build());
    return new OpenSearchAsyncClient(transport);
  }

  private OpenSearchClient getAwsClient(final OpenSearchProperties osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkHttpClient httpClient = AwsCrtHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            osConfig.getHost(),
            Region.of(region),
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(tasklistObjectMapper))
                .build());
    return new OpenSearchClient(transport);
  }

  private org.apache.http.impl.nio.client.HttpAsyncClientBuilder configureApacheHttpClient(
      final org.apache.http.impl.nio.client.HttpAsyncClientBuilder builder,
      final OpenSearchProperties osConfig,
      final org.apache.http.HttpRequestInterceptor... httpRequestInterceptors) {

    for (final HttpRequestInterceptor httpRequestInterceptor : httpRequestInterceptors) {
      builder.addInterceptorLast(httpRequestInterceptor);
    }

    if (hasAwsCredentials()) {
      configureAwsSigningForApacheHttpClient(builder);
    } else if (useBasicAuthentication(osConfig)) {
      configureBasicAuthenticationForApacheHttpClient(osConfig, builder);
    }

    return builder;
  }

  private void configureAwsSigningForApacheHttpClient(
      final org.apache.http.impl.nio.client.HttpAsyncClientBuilder builder) {
    final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
    credentialsProvider.resolveCredentials();
    final Aws4Signer signer = Aws4Signer.create();
    final HttpRequestInterceptor signInterceptor =
        new AwsRequestSigningApacheInterceptor(
            AWS_OPENSEARCH_SERVICE_NAME,
            signer,
            credentialsProvider,
            new DefaultAwsRegionProviderChain().getRegion());
    builder.addInterceptorLast(signInterceptor);
  }

  private void configureBasicAuthenticationForApacheHttpClient(
      final OpenSearchProperties osConfig,
      final org.apache.http.impl.nio.client.HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider =
        new org.apache.http.impl.client.BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new org.apache.http.auth.AuthScope(getHttpHost(osConfig)),
        new org.apache.http.auth.UsernamePasswordCredentials(
            osConfig.getUsername(), osConfig.getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private boolean useBasicAuthentication(final OpenSearchProperties osConfig) {
    return StringUtils.hasText(osConfig.getUsername())
        && StringUtils.hasText(osConfig.getPassword());
  }
}
