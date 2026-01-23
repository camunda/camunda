/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.OpenSearchProperties;
import io.camunda.tasklist.property.ProxyProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.util.VisibleForTesting;
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
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
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
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

@Configuration
@Conditional(OpenSearchCondition.class)
public class OpenSearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchConnector.class);

  private PluginRepository osClientRepository = new PluginRepository();
  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper tasklistObjectMapper;

  @VisibleForTesting
  public void setOsClientRepository(final PluginRepository osClientRepository) {
    this.osClientRepository = osClientRepository;
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
    checkHealth(openSearchClient);
    return openSearchClient;
  }

  public OpenSearchClient createOsClient(
      final OpenSearchProperties osConfig, final PluginRepository pluginRepository) {
    LOGGER.debug("Creating OpenSearch connection...");
    if (hasAwsCredentials()) {
      return getAwsClient(osConfig);
    }
    final HttpHost[] hosts = getHttpHostsForClient5(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(hosts);

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

    checkHealth(openSearchClient);
    return openSearchClient;
  }

  private HttpHost getHttpHostForClient5(final OpenSearchProperties osConfig) {
    final URI uri = getOsUri(osConfig);
    return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
  }

  private HttpHost[] getHttpHostsForClient5(final OpenSearchProperties osConfig) {
    final var urls = osConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      return urls.stream()
          .map(
              url -> {
                try {
                  final URI uri = new URI(url);
                  return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
                } catch (final URISyntaxException e) {
                  throw new TasklistRuntimeException("Error in url: " + url, e);
                }
              })
          .toArray(HttpHost[]::new);
    }
    return new HttpHost[] {getHttpHostForClient5(osConfig)};
  }

  private URI getOsUri(final OpenSearchProperties osConfig) {
    try {
      return new URI(osConfig.getUrl());
    } catch (final URISyntaxException e) {
      throw new TasklistRuntimeException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private void setupAuthentication(
      final BasicCredentialsProvider credentialsProvider, final OpenSearchProperties osConfig) {
    if (!useBasicAuthentication(osConfig)) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return;
    }

    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            osConfig.getUsername(), osConfig.getPassword().toCharArray()));
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
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();

    setupAuthentication(credentialsProvider, osConfig);

    LOGGER.trace("Attempt to load interceptor plugins");
    for (final var interceptor : httpRequestInterceptors) {
      httpAsyncClientBuilder.addRequestInterceptorFirst(interceptor);
    }

    if (osConfig.getSsl() != null) {
      setupSSLContext(httpAsyncClientBuilder, osConfig.getSsl());
    }

    final ProxyProperties proxyConfig = osConfig.getProxy();
    if (proxyConfig != null && proxyConfig.isEnabled()) {
      setupProxy(httpAsyncClientBuilder, proxyConfig);
      setupProxyAuthentication(credentialsProvider, proxyConfig);
    }

    httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

    return httpAsyncClientBuilder;
  }

  private void setupProxy(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final ProxyProperties proxyConfig) {
    httpAsyncClientBuilder.setProxy(
        new HttpHost(
            proxyConfig.isSslEnabled() ? "https" : "http",
            proxyConfig.getHost(),
            proxyConfig.getPort()));
    LOGGER.debug(
        "Using proxy {}:{} for OpenSearch connection",
        proxyConfig.getHost(),
        proxyConfig.getPort());
  }

  private void setupProxyAuthentication(
      final BasicCredentialsProvider credentialsProvider, final ProxyProperties proxyConfig) {
    final String username = proxyConfig.getUsername();
    final String password = proxyConfig.getPassword();

    if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
      return;
    }

    final HttpHost proxyHost =
        new HttpHost(
            proxyConfig.isSslEnabled() ? "https" : "http",
            proxyConfig.getHost(),
            proxyConfig.getPort());
    credentialsProvider.setCredentials(
        new AuthScope(proxyHost),
        new UsernamePasswordCredentials(
            proxyConfig.getUsername(), proxyConfig.getPassword().toCharArray()));
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

  private void checkHealth(final OpenSearchClient openSearchClient) {
    if (!isHealthy(openSearchClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("OpenSearch connection was successfully created.");
    }
  }

  @VisibleForTesting
  public boolean isHealthy(final OpenSearchClient osClient) {
    final OpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    if (!osConfig.isHealthCheckEnabled()) {
      LOGGER.debug("Opensearch health check is disabled");
      return true;
    }
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final HealthResponse clusterHealthResponse = osClient.cluster().health();
              return clusterHealthResponse.clusterName().equals(osConfig.getClusterName());
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
    try (final var credentialsProvider = DefaultCredentialsProvider.builder().build()) {
      credentialsProvider.resolveCredentials();
      LOGGER.info("AWS Credentials can be resolved. Use AWS Opensearch");
      return true;
    } catch (final Exception e) {
      LOGGER.warn("AWS not configured due to: {} ", e.getMessage());
      return false;
    }
  }

  private OpenSearchClient getAwsClient(final OpenSearchProperties osConfig) {
    final var region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkHttpClient httpClient = AwsCrtHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            getHttpHostsForClient5(osConfig)[0].getHostName(),
            region,
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(tasklistObjectMapper))
                .build());
    return new OpenSearchClient(transport);
  }

  private boolean useBasicAuthentication(final OpenSearchProperties osConfig) {
    return StringUtils.hasText(osConfig.getUsername())
        && StringUtils.hasText(osConfig.getPassword());
  }
}
