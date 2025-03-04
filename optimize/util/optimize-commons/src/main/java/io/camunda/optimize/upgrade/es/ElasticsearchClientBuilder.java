/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.se.config.PluginConfiguration;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import io.camunda.optimize.service.util.configuration.ProxyConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchClientBuilder {

  private static final String HTTP = "http";
  private static final String HTTPS = "https";
  private static TransportOptions transportOptions;

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchClientBuilder.class);

  public static ElasticsearchClient build(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final PluginRepository pluginRepository) {
    transportOptions = getTransportOptions(configurationService);
    return getElasticsearchClient(restClient(configurationService, pluginRepository), objectMapper);
  }

  public static String getCurrentESVersion(
      final ElasticsearchClient esClient, final TransportOptions requestOptions)
      throws IOException {
    return esClient.withTransportOptions(requestOptions).info().version().number();
  }

  public static RestClient restClient(
      final ConfigurationService configurationService, final PluginRepository pluginRepository) {
    final List<PluginConfiguration> plugins =
        extractPluginConfigs(configurationService.getElasticSearchConfiguration());
    pluginRepository.load(plugins);
    if (configurationService.getElasticSearchConfiguration().getSecuritySSLEnabled()) {
      LOGGER.info("Setting up https rest client connection");
      final RestClientBuilder builder =
          buildDefaultRestClient(
              configurationService, HTTPS, pluginRepository.asRequestInterceptor());
      try {

        final SSLContext sslContext;
        final KeyStore truststore = loadCustomTrustStore(configurationService);

        if (truststore.size() > 0) {
          final TrustStrategy trustStrategy =
              configurationService.getElasticSearchConfiguration().getSecuritySslSelfSigned()
                      == Boolean.TRUE
                  ? new TrustSelfSignedStrategy()
                  : null;
          sslContext = SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
        } else {
          // default if custom truststore is empty
          sslContext = SSLContext.getDefault();
        }

        builder.setHttpClientConfigCallback(
            createHttpClientConfigCallback(configurationService, sslContext));
      } catch (final Exception e) {
        final String message = "Could not build secured Elasticsearch client.";
        throw new OptimizeRuntimeException(message, e);
      }
      return builder.build();
    } else {
      LOGGER.info("Setting up http rest client connection");
      return buildDefaultRestClient(
              configurationService, HTTP, pluginRepository.asRequestInterceptor())
          .build();
    }
  }

  private static RestClientBuilder.HttpClientConfigCallback createHttpClientConfigCallback(
      final ConfigurationService configurationService) {
    return createHttpClientConfigCallback(configurationService, null);
  }

  private static RestClientBuilder.HttpClientConfigCallback createHttpClientConfigCallback(
      final ConfigurationService configurationService,
      final SSLContext sslContext,
      final HttpRequestInterceptor... requestInterceptors) {
    return httpClientBuilder -> {
      buildCredentialsProviderIfConfigured(configurationService)
          .ifPresent(httpClientBuilder::setDefaultCredentialsProvider);

      for (final HttpRequestInterceptor interceptor : requestInterceptors) {
        httpClientBuilder.addInterceptorLast(interceptor);
      }

      httpClientBuilder.setSSLContext(sslContext);

      final ProxyConfiguration proxyConfig =
          configurationService.getElasticSearchConfiguration().getProxyConfig();
      if (proxyConfig.isEnabled()) {
        httpClientBuilder.setProxy(
            new HttpHost(
                proxyConfig.getHost(),
                proxyConfig.getPort(),
                proxyConfig.isSslEnabled() ? HTTPS : HTTP));
      }

      if (configurationService.getElasticSearchConfiguration().getSkipHostnameVerification()) {
        // setting this to always be true essentially skips the hostname verification
        httpClientBuilder.setSSLHostnameVerifier((s, sslSession) -> true);
      }

      return httpClientBuilder;
    };
  }

  private static RestClientBuilder buildDefaultRestClient(
      final ConfigurationService configurationService,
      final String protocol,
      final HttpRequestInterceptor... requestInterceptors) {
    final RestClientBuilder restClientBuilder =
        RestClient.builder(buildElasticsearchConnectionNodes(configurationService, protocol))
            .setRequestConfigCallback(
                requestConfigBuilder ->
                    requestConfigBuilder
                        .setConnectTimeout(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getConnectionTimeout())
                        .setSocketTimeout(0));
    if (!StringUtils.isEmpty(
        configurationService.getElasticSearchConfiguration().getPathPrefix())) {
      restClientBuilder.setPathPrefix(
          configurationService.getElasticSearchConfiguration().getPathPrefix());
    }

    restClientBuilder.setHttpClientConfigCallback(
        createHttpClientConfigCallback(configurationService, null, requestInterceptors));

    return restClientBuilder;
  }

  private static HttpHost[] buildElasticsearchConnectionNodes(
      final ConfigurationService configurationService, final String protocol) {
    return configurationService.getElasticSearchConfiguration().getConnectionNodes().stream()
        .map(conf -> new HttpHost(conf.getHost(), conf.getHttpPort(), protocol))
        .toArray(HttpHost[]::new);
  }

  private static Optional<CredentialsProvider> buildCredentialsProviderIfConfigured(
      final ConfigurationService configurationService) {
    CredentialsProvider credentialsProvider = null;
    if (configurationService.getElasticSearchConfiguration().getSecurityUsername() != null
        && configurationService.getElasticSearchConfiguration().getSecurityPassword() != null) {
      credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(
              configurationService.getElasticSearchConfiguration().getSecurityUsername(),
              configurationService.getElasticSearchConfiguration().getSecurityPassword()));
    } else {
      LOGGER.debug(
          "Elasticsearch username and password not provided, skipping connection credential setup.");
    }
    return Optional.ofNullable(credentialsProvider);
  }

  private static KeyStore loadCustomTrustStore(final ConfigurationService configurationService) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);

      // load custom es server certificate if configured
      final String serverCertificate =
          configurationService.getElasticSearchConfiguration().getSecuritySSLCertificate();
      if (serverCertificate != null) {
        try {
          final Certificate cert = loadCertificateFromPath(serverCertificate);
          trustStore.setCertificateEntry("elasticsearch-host", cert);
        } catch (final Exception e) {
          final String message =
              "Could not load configured server certificate for the secured Elasticsearch Connection!";
          throw new OptimizeConfigurationException(message, e);
        }
      }

      // load trusted CA certificates
      int caCertificateCounter = 0;
      for (final String caCertificatePath :
          configurationService
              .getElasticSearchConfiguration()
              .getSecuritySSLCertificateAuthorities()) {
        try {
          final Certificate cert = loadCertificateFromPath(caCertificatePath);
          trustStore.setCertificateEntry("custom-elasticsearch-ca-" + caCertificateCounter, cert);
          caCertificateCounter++;
        } catch (final Exception e) {
          final String message =
              "Could not load CA authority certificate for the secured Elasticsearch Connection!";
          throw new OptimizeConfigurationException(message, e);
        }
      }

      return trustStore;
    } catch (final Exception e) {
      final String message =
          "Could not create certificate trustStore for the secured Elasticsearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath)
      throws IOException, CertificateException {
    final Certificate cert;
    final FileInputStream fileInputStream = new FileInputStream(certificatePath);
    try (final BufferedInputStream bis = new BufferedInputStream(fileInputStream)) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        LOGGER.debug("Found certificate: {}", cert);
      } else {
        throw new OptimizeConfigurationException(
            "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }

  private static ElasticsearchClient getElasticsearchClient(
      final RestClient builder, final ObjectMapper objectMapper) {
    LOGGER.info("Finished setting up HTTP rest client connection.");
    return new ElasticsearchClient(
        new RestClientTransport(builder, new JacksonJsonpMapper(objectMapper)), transportOptions);
  }

  public static TransportOptions getTransportOptions(
      final ConfigurationService configurationService) {
    final TransportOptionsProvider transportOptionsProvider =
        new TransportOptionsProvider(configurationService);
    return transportOptionsProvider.getTransportOptions();
  }

  private static List<PluginConfiguration> extractPluginConfigs(
      final ElasticSearchConfiguration esConfig) {
    final Map<String, PluginConfiguration> pluginConfigs = esConfig.getInterceptorPlugins();
    if (pluginConfigs != null) {
      return pluginConfigs.values().stream()
          .filter(plugin -> StringUtils.isNotBlank(plugin.id()))
          .toList();
    }
    return List.of();
  }
}
