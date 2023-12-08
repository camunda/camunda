/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.elasticsearch.client.RequestOptions;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.util.DatabaseVersionChecker.checkOSVersionSupport;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Conditional(OpenSearchCondition.class)
@Slf4j
public class OptimizeOpenSearchClientFactory {

  public static OptimizeOpenSearchClient create(final ConfigurationService configurationService,
                                                final OptimizeIndexNameService optimizeIndexNameService,
                                                final OpenSearchSchemaManager openSearchSchemaManager,
                                                final BackoffCalculator backoffCalculator) throws IOException {

    log.info("Creating OpenSearch connection...");
    // TODO Evaluate the need for OpenSearchCustomHeaderProvider with OPT-7400
    final RequestOptionsProvider requestOptionsProvider =
      new RequestOptionsProvider(Collections.emptyList(), configurationService);
    final HttpHost host = getHttpHost(configurationService.getOpenSearchConfiguration().getFirstConnectionNode());
    final ApacheHttpClient5TransportBuilder builder =
      ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
      httpClientBuilder -> {
        configureHttpClient(httpClientBuilder, configurationService);
        return httpClientBuilder;
      });

    builder.setRequestConfigCallback(
      requestConfigBuilder -> {
        setTimeouts(requestConfigBuilder, configurationService);
        return requestConfigBuilder;
      });

    ObjectMapper objectMapper = new ObjectMapper();

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));
    objectMapper.registerModule(javaTimeModule);
    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
    builder.setMapper(jsonpMapper);

    final OpenSearchTransport transport = builder.build();
    final OpenSearchClient openSearchClient = new OpenSearchClient(transport);
    waitForOpenSearch(openSearchClient, backoffCalculator, requestOptionsProvider.getRequestOptions());
    log.info("OpenSearch cluster successfully started");

    OptimizeOpenSearchClient osClient =  new OptimizeOpenSearchClient(openSearchClient, optimizeIndexNameService,
                                                                      requestOptionsProvider);
    openSearchSchemaManager.validateExistingSchemaVersion(osClient);
    openSearchSchemaManager.initializeSchema(osClient);

    return osClient;
  }

  private static void waitForOpenSearch(final OpenSearchClient osClient,
                                       final BackoffCalculator backoffCalculator,
                                       final RequestOptions requestOptions) throws IOException {
    boolean isConnected = false;
    while (!isConnected) {
      try {
        isConnected = getNumberOfClusterNodes(osClient) > 0;
      } catch (final Exception e) {
        log.error(
          "Can't connect to any OpenSearch node {}. Please check the connection!",
          osClient.nodes(), e
        );
      } finally {
        if (!isConnected) {
          long sleepTime = backoffCalculator.calculateSleepTime();
          log.info("No OpenSearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
          try {
            Thread.sleep(sleepTime);
          } catch (final InterruptedException e) {
            log.warn("Got interrupted while waiting to retry connecting to OpenSearch.", e);
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    checkOSVersionSupport(osClient, requestOptions);
  }

  public static String getCurrentOSVersion(final OpenSearchClient osClient) throws IOException {
    return osClient.info().version().number();
  }

  private static int getNumberOfClusterNodes(final OpenSearchClient openSearchClient) throws IOException {
    return openSearchClient.cluster().health().numberOfNodes();
  }

  private static HttpHost getHttpHost(DatabaseConnectionNodeConfiguration configuration) {
    String uriConfig = String.format("http://%s:%d", configuration.getHost(), configuration.getHttpPort());
    try {
      final URI uri = new URI(uriConfig);
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException("Error in url: " + uriConfig, e);
    }
  }

  private static HttpAsyncClientBuilder setupAuthentication(
    final HttpAsyncClientBuilder builder, ConfigurationService configurationService) {
    String username = configurationService.getOpenSearchConfiguration().getSecurityUsername();
    String password = configurationService.getOpenSearchConfiguration().getSecurityPassword();
    if (!StringUtils.hasText(username)
      || !StringUtils.hasText(password)) {
      log.warn(
        "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
      new AuthScope(getHttpHost(configurationService.getOpenSearchConfiguration().getFirstConnectionNode())),
      new UsernamePasswordCredentials(
        username, password.toCharArray())
    );

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private static void setupSSLContext(
    HttpAsyncClientBuilder httpAsyncClientBuilder, ConfigurationService configurationService) {
    try {
      final ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      tlsStrategyBuilder.setSslContext(getSSLContext(configurationService));
      if (Boolean.TRUE.equals(configurationService.getOpenSearchConfiguration().getSkipHostnameVerification())) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      final TlsStrategy tlsStrategy = tlsStrategyBuilder.build();
      final PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);

    } catch (Exception e) {
      log.error("Error in setting up SSLContext", e);
    }
  }

  private static SSLContext getSSLContext(ConfigurationService configurationService)

    throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    final KeyStore truststore = loadCustomTrustStore(configurationService);
    final TrustStrategy trustStrategy =
      Boolean.TRUE.equals(configurationService.getOpenSearchConfiguration()
        .getSecuritySslSelfSigned()) ? new TrustSelfSignedStrategy() : null;
    if (truststore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
    } else {
      // default if custom truststore is empty
      return SSLContext.getDefault();
    }
  }

  private static KeyStore loadCustomTrustStore(ConfigurationService configurationService) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      // load custom es server certificate if configured
      final String serverCertificate = configurationService.getOpenSearchConfiguration().getSecuritySSLCertificate();
      if (serverCertificate != null) {
        setCertificateInTrustStore(trustStore, serverCertificate);
      }
      return trustStore;
    } catch (Exception e) {
      final String message =
        "Could not create certificate trustStore for the secured OpenSearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static void setCertificateInTrustStore(
    final KeyStore trustStore, final String serverCertificate) {
    try {
      final Certificate cert = loadCertificateFromPath(serverCertificate);
      trustStore.setCertificateEntry("opensearch-host", cert);
    } catch (Exception e) {
      final String message =
        "Could not load configured server certificate for the secured OpenSearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath)
    throws IOException, CertificateException {
    final Certificate cert;
    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(certificatePath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        log.debug("Found certificate: {}", cert);
      } else {
        throw new OptimizeRuntimeException(
          "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }

  private static HttpAsyncClientBuilder configureHttpClient(HttpAsyncClientBuilder httpAsyncClientBuilder,
                                                            ConfigurationService configurationService) {
    setupAuthentication(httpAsyncClientBuilder, configurationService);
    if (Boolean.TRUE.equals(configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled())) {
      setupSSLContext(httpAsyncClientBuilder, configurationService);
    }
    return httpAsyncClientBuilder;
  }

  private static RequestConfig.Builder setTimeouts(
    final RequestConfig.Builder builder, final ConfigurationService configurationService) {
    builder.setResponseTimeout(Timeout.ofMilliseconds(configurationService.getOpenSearchConfiguration().getConnectionTimeout()));
    builder.setConnectTimeout(Timeout.ofMilliseconds(configurationService.getOpenSearchConfiguration().getConnectionTimeout()));
    return builder;
  }

}
