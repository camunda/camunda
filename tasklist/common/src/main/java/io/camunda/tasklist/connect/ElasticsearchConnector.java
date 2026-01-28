/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.connect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.ElasticsearchProperties;
import io.camunda.tasklist.property.SslProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.RetryOperation;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
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
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Configuration
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  private PluginRepository esClientRepository = new PluginRepository();
  @Autowired private TasklistProperties tasklistProperties;

  @VisibleForTesting
  public void setEsClientRepository(final PluginRepository esClientRepository) {
    this.esClientRepository = esClientRepository;
  }

  @VisibleForTesting
  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  @Bean(destroyMethod = "close")
  public RestHighLevelClient tasklistEsClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    esClientRepository.load(tasklistProperties.getElasticsearch().getInterceptorPlugins());
    return createEsClient(tasklistProperties.getElasticsearch(), esClientRepository);
  }

  protected RestHighLevelClient createEsClient(
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
    if (!checkHealth(esClient)) {
      LOGGER.warn("Elasticsearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return esClient;
  }

  @Bean
  public ElasticsearchClient tasklistEs8Client() {
    esClientRepository.load(tasklistProperties.getElasticsearch().getInterceptorPlugins());
    return createEs8Client(tasklistProperties.getElasticsearch(), esClientRepository);
  }

  public ElasticsearchClient createEs8Client(
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

    final RestClientTransport transport =
        new RestClientTransport(
            restClientBuilder.build(), new JacksonJsonpMapper(CommonUtils.OBJECT_MAPPER));

    final var client = new ElasticsearchClient(transport);

    if (tasklistProperties.getElasticsearch().isHealthCheckEnabled()) {
      if (!checkHealth(client)) {
        LOGGER.warn("Elasticsearch cluster is not accessible");
      } else {
        LOGGER.debug("Elasticsearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("Elasticsearch cluster health check is disabled.");
    }
    return client;
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ElasticsearchProperties elsConfig,
      final HttpRequestInterceptor... interceptors) {
    setupAuthentication(httpAsyncClientBuilder, elsConfig);

    LOGGER.trace("Attempt to load interceptor plugins");
    for (final var interceptor : interceptors) {
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
      final String certificatePath = sslConfig.getCertificatePath();
      if (certificatePath != null) {
        // Check if it's a PKCS12 keystore
        if (certificatePath.endsWith(".p12") || certificatePath.endsWith(".pfx")) {
          return loadPKCS12KeyStore(certificatePath);
        } else {
          // Load as X.509 certificate
          return loadX509KeyStore(certificatePath);
        }
      } else {
        final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        return trustStore;
      }
    } catch (final Exception e) {
      throw new TasklistRuntimeException(
          "Could not create certificate trustStore for the secured Elasticsearch Connection!", e);
    }
  }

  private KeyStore loadPKCS12KeyStore(final String certificatePath) {
    try (final FileInputStream fis = new FileInputStream(certificatePath)) {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(fis, null); // No password for the test keystore
      return keyStore;
    } catch (final Exception e) {
      throw new TasklistRuntimeException(
          "Could not load PKCS12 certificate from path: " + certificatePath, e);
    }
  }

  private KeyStore loadX509KeyStore(final String certificatePath) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      final Certificate cert = loadCertificateFromPath(certificatePath);
      trustStore.setCertificateEntry("elasticsearch-host", cert);
      return trustStore;
    } catch (final Exception e) {
      throw new TasklistRuntimeException(
          "Could not load X.509 certificate from path: " + certificatePath, e);
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

  public boolean checkHealth(final RestHighLevelClient esClient) {
    final ElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    if (!elsConfig.isHealthCheckEnabled()) {
      LOGGER.debug("Elasticsearch health check is disabled");
      return true;
    }
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(elsConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final ClusterHealthResponse clusterHealthResponse =
                  esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
              return clusterHealthResponse.getClusterName().equals(elsConfig.getClusterName());
            });
  }

  boolean checkHealth(final ElasticsearchClient esClient) {
    final ElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
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
                final HealthResponse clusterHealthResponse = esClient.cluster().health();
                return clusterHealthResponse.clusterName().equals(elsConfig.getClusterName());
              })
          .build()
          .retry();
    } catch (final Exception e) {
      throw new TasklistRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  private RetryPolicy<Boolean> getConnectionRetryPolicy(final ElasticsearchProperties elsConfig) {
    final String logMessage = String.format("connect to Elasticsearch at %s", elsConfig.getUrl());
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
      throw new TasklistRuntimeException("Error in url: " + elsConfig.getUrl(), e);
    }
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final ElasticsearchProperties elsConfig) {
    if (!StringUtils.hasText(elsConfig.getUsername())
        || !StringUtils.hasText(elsConfig.getPassword())) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
      return builder;
    }
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(elsConfig.getUsername(), elsConfig.getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  public static class CustomOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

    private final DateTimeFormatter formatter;

    public CustomOffsetDateTimeSerializer(final DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public void serialize(
        final OffsetDateTime value, final JsonGenerator gen, final SerializerProvider provider)
        throws IOException {
      if (value == null) {
        gen.writeNull();
      } else {
        gen.writeString(value.format(formatter));
      }
    }
  }

  public static class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private final DateTimeFormatter formatter;

    public CustomOffsetDateTimeDeserializer(final DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    @Override
    public OffsetDateTime deserialize(final JsonParser parser, final DeserializationContext context)
        throws IOException {

      final OffsetDateTime parsedDate;
      try {
        parsedDate = OffsetDateTime.parse(parser.getText(), formatter);
      } catch (final DateTimeParseException exception) {
        throw new TasklistRuntimeException(
            "Exception occurred when deserializing date.", exception);
      }
      return parsedDate;
    }
  }

  public static class CustomInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(final JsonParser parser, final DeserializationContext context)
        throws IOException {
      return Instant.ofEpochMilli(Long.parseLong(parser.getText()));
    }
  }
}
