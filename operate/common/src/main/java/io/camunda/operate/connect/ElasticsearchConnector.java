/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.config.operate.ElasticsearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.config.operate.SslProperties;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.RetryOperation;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.util.VisibleForTesting;
import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Conditional(ElasticsearchCondition.class)
@Configuration
public class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  private PluginRepository esClientRepository = new PluginRepository();
  private PluginRepository zeebeEsClientRepository = new PluginRepository();
  private final OperateProperties operateProperties;
  private ElasticsearchClient elasticsearchClient;

  public ElasticsearchConnector(final OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
  }

  @VisibleForTesting
  public void setEsClientRepository(final PluginRepository esClientRepository) {
    this.esClientRepository = esClientRepository;
  }

  @VisibleForTesting
  public void setZeebeEsClientRepository(final PluginRepository zeebeEsClientRepository) {
    this.zeebeEsClientRepository = zeebeEsClientRepository;
  }

  public static void closeEsClient(final RestHighLevelClient esClient) {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (final IOException e) {
        LOGGER.error("Could not close esClient", e);
      }
    }
  }

  public static void closeEsClient(final ElasticsearchClient esClient) {
    if (esClient != null) {
      esClient.shutdown();
    }
  }

  @Bean
  public ElasticsearchClient elasticsearchClient() {
    LOGGER.debug("Creating ElasticsearchClient ...");
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();

    esClientRepository.load(operateProperties.getElasticsearch().getInterceptorPlugins());

    final RestClientBuilder restClientBuilder = RestClient.builder(getHttpHost(elsConfig));
    if (elsConfig.getConnectTimeout() != null || elsConfig.getSocketTimeout() != null) {
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setTimeouts(configCallback, elsConfig));
    }
    final RestClient restClient =
        restClientBuilder
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    configureHttpClient(
                        httpClientBuilder, elsConfig, esClientRepository.asRequestInterceptor()))
            .build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    // And create the API client
    elasticsearchClient = new ElasticsearchClient(transport);

    if (operateProperties.getElasticsearch().isHealthCheckEnabled()) {
      if (!checkHealth(elasticsearchClient)) {
        LOGGER.warn("Elasticsearch cluster is not accessible");
      } else {
        LOGGER.debug("Elasticsearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("Elasticsearch cluster health check is disabled.");
    }
    return elasticsearchClient;
  }

  public boolean checkHealth(final ElasticsearchClient elasticsearchClient) {
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(50)
          .retryOn(
              IOException.class,
              co.elastic.clients.elasticsearch._types.ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message(
              String.format(
                  "Connect to Elasticsearch cluster [%s] at %s",
                  elsConfig.getClusterName(), elsConfig.getUrl()))
          .retryConsumer(
              () -> {
                final HealthResponse healthResponse = elasticsearchClient.cluster().health();
                LOGGER.info("Elasticsearch cluster health: {}", healthResponse.status());
                return healthResponse.clusterName().equals(elsConfig.getClusterName());
              })
          .build()
          .retry();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  @Bean
  public RestHighLevelClient esClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    esClientRepository.load(operateProperties.getElasticsearch().getInterceptorPlugins());
    return createEsClient(operateProperties.getElasticsearch(), esClientRepository);
  }

  @Bean("zeebeEsClient")
  public RestHighLevelClient zeebeEsClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    zeebeEsClientRepository.load(operateProperties.getZeebeElasticsearch().getInterceptorPlugins());
    return createEsClient(operateProperties.getZeebeElasticsearch(), zeebeEsClientRepository);
  }

  @PreDestroy
  public void tearDown() {
    if (elasticsearchClient != null) {
      try {
        elasticsearchClient._transport().close();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public RestHighLevelClient createEsClient(
      final ElasticsearchProperties elsConfig, final PluginRepository pluginRepository) {
    LOGGER.debug("Creating Elasticsearch connection...");
    final RestClientBuilder restClientBuilder =
        RestClient.builder(getHttpHost(elsConfig))
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    configureHttpClient(
                        httpClientBuilder, elsConfig, pluginRepository.asRequestInterceptor()));
    if (elsConfig.getConnectTimeout() != null || elsConfig.getSocketTimeout() != null) {
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setTimeouts(configCallback, elsConfig));
    }
    final RestHighLevelClient esClient =
        new RestHighLevelClientBuilder(restClientBuilder.build())
            .setApiCompatibilityMode(true)
            .build();
    if (operateProperties.getElasticsearch().isHealthCheckEnabled()) {
      if (!checkHealth(esClient)) {
        LOGGER.warn("Elasticsearch cluster is not accessible");
      } else {
        LOGGER.debug("Elasticsearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("Elasticsearch cluster health check is disabled.");
    }
    return esClient;
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ElasticsearchProperties elsConfig,
      final HttpRequestInterceptor... interceptors) {
    setupAuthentication(httpAsyncClientBuilder, elsConfig);

    LOGGER.trace("Attempt to load interceptor plugins");
    for (final HttpRequestInterceptor interceptor : interceptors) {
      httpAsyncClientBuilder.addInterceptorLast(interceptor);
    }

    if (elsConfig.getSsl() != null) {
      setupSSLContext(httpAsyncClientBuilder, elsConfig.getSsl());
    }
    return httpAsyncClientBuilder;
  }

  private void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final SslProperties sslConfig) {
    try {
      httpAsyncClientBuilder.setSSLContext(getSSLContext(sslConfig));
      if (!sslConfig.isVerifyHostname()) {
        httpAsyncClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }
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
          "Could not create certificate trustStore for the secured Elasticsearch Connection!";
      throw new OperateRuntimeException(message, e);
    }
  }

  private void setCertificateInTrustStore(
      final KeyStore trustStore, final String serverCertificate) {
    try {
      final Certificate cert = loadCertificateFromPath(serverCertificate);
      trustStore.setCertificateEntry("elasticsearch-host", cert);
    } catch (final Exception e) {
      final String message =
          "Could not load configured server certificate for the secured Elasticsearch Connection!";
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

  private Builder setTimeouts(final Builder builder, final ElasticsearchProperties elsConfig) {
    if (elsConfig.getSocketTimeout() != null) {
      builder.setSocketTimeout(elsConfig.getSocketTimeout());
    }
    if (elsConfig.getConnectTimeout() != null) {
      builder.setConnectTimeout(elsConfig.getConnectTimeout());
    }
    return builder;
  }

  private HttpHost getHttpHost(final ElasticsearchProperties elsConfig) {
    try {
      final URI uri = new URI(elsConfig.getUrl());
      return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    } catch (final URISyntaxException e) {
      throw new OperateRuntimeException("Error in url: " + elsConfig.getUrl(), e);
    }
  }

  private void setupAuthentication(
      final HttpAsyncClientBuilder builder, final ElasticsearchProperties elsConfig) {
    final String username = elsConfig.getUsername();
    final String password = elsConfig.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
      return;
    }
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  public boolean checkHealth(final RestHighLevelClient esClient) {
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(50)
          .retryOn(IOException.class, ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message(
              String.format(
                  "Connect to Elasticsearch cluster [%s] at %s",
                  elsConfig.getClusterName(), elsConfig.getUrl()))
          .retryConsumer(
              () -> {
                final ClusterHealthResponse clusterHealthResponse =
                    esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
                return clusterHealthResponse.getClusterName().equals(elsConfig.getClusterName());
              })
          .build()
          .retry();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }
}
