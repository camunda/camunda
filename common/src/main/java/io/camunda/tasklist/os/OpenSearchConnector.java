/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.OpenSearchProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
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
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@Conditional(OpenSearchCondition.class)
public class OpenSearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchConnector.class);

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private ObjectMapper tasklistObjectMapper;

  @Bean
  public OpenSearchClient openSearchClient() {
    final OpenSearchClient openSearchClient = createOsClient(tasklistProperties.getOpenSearch());
    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (IOException e) {
      LOGGER.error(
          "Error in getting health status from localhost:"
              + tasklistProperties.getOpenSearch().getPort(),
          e);
    }
    return openSearchClient;
  }

  @Bean
  public RestClient opensearchRestClient() {
    final var originalHttpHost = getHttpHost(tasklistProperties.getOpenSearch());
    final org.apache.http.HttpHost httpHost =
        new org.apache.http.HttpHost(
            originalHttpHost.getHostName(),
            originalHttpHost.getPort(),
            originalHttpHost.getSchemeName());
    return RestClient.builder(httpHost).build();
  }

  @Bean
  public RestClient opensearchZeebeRestClient() {
    final var originalHttpHost = getHttpHost(tasklistProperties.getZeebeOpenSearch());
    final org.apache.http.HttpHost httpHost =
        new org.apache.http.HttpHost(
            originalHttpHost.getHostName(),
            originalHttpHost.getPort(),
            originalHttpHost.getSchemeName());
    return RestClient.builder(httpHost).build();
  }

  @Bean
  public OpenSearchAsyncClient openSearchAsyncClient() {
    final OpenSearchAsyncClient openSearchClient =
        createAsyncOsClient(tasklistProperties.getOpenSearch());
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
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return openSearchClient;
  }

  @Bean("zeebeOsClient")
  public OpenSearchClient zeebeOsClient() {
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createOsClient(tasklistProperties.getZeebeOpenSearch());
  }

  public OpenSearchAsyncClient createAsyncOsClient(OpenSearchProperties osConfig) {
    LOGGER.debug("Creating Async OpenSearch connection...");
    LOGGER.debug("Creating OpenSearch connection...");
    final HttpHost host = getHttpHost(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(httpClientBuilder, osConfig);
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
    } catch (IOException e) {
      throw new TasklistRuntimeException(e);
    }

    if (!checkHealth(openSearchAsyncClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("OpenSearch connection was successfully created.");
    }
    return openSearchAsyncClient;
  }

  public OpenSearchClient createOsClient(OpenSearchProperties osConfig) {
    LOGGER.debug("Creating OpenSearch connection...");
    final HttpHost host = getHttpHost(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(httpClientBuilder, osConfig);
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
    } catch (IOException e) {
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

  private HttpHost getHttpHost(OpenSearchProperties osConfig) {
    try {
      final URI uri = new URI(osConfig.getUrl());
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (URISyntaxException e) {
      throw new TasklistRuntimeException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, OpenSearchProperties osConfig) {
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
      HttpAsyncClientBuilder httpAsyncClientBuilder, SslProperties sslConfig) {
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

    } catch (Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }

  private SSLContext getSSLContext(SslProperties sslConfig)
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

  private KeyStore loadCustomTrustStore(SslProperties sslConfig) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      // load custom es server certificate if configured
      final String serverCertificate = sslConfig.getCertificatePath();
      if (serverCertificate != null) {
        setCertificateInTrustStore(trustStore, serverCertificate);
      }
      return trustStore;
    } catch (Exception e) {
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
    } catch (Exception e) {
      final String message =
          "Could not load configured server certificate for the secured OpenSearch Connection!";
      throw new TasklistRuntimeException(message, e);
    }
  }

  private Certificate loadCertificateFromPath(final String certificatePath)
      throws IOException, CertificateException {
    final Certificate cert;
    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(certificatePath))) {
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

  private HttpAsyncClientBuilder configureHttpClient(
      HttpAsyncClientBuilder httpAsyncClientBuilder, OpenSearchProperties osConfig) {
    setupAuthentication(httpAsyncClientBuilder, osConfig);
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

  public boolean checkHealth(OpenSearchClient osClient) {
    final OpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final HealthResponse clusterHealthResponse =
                  osClient.cluster().health(new HealthRequest.Builder().build());
              return clusterHealthResponse.clusterName().equals(osConfig.getClusterName());
            });
  }

  public boolean checkHealth(OpenSearchAsyncClient osAsyncClient) {
    final OpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
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
}
