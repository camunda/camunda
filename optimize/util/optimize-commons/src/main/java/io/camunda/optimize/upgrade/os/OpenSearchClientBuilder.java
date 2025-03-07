/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.os;

import static io.camunda.optimize.rest.constants.RestConstants.HTTPS_PREFIX;
import static io.camunda.optimize.rest.constants.RestConstants.HTTP_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.rest.exceptions.NotSupportedException;
import io.camunda.optimize.service.db.os.ExtendedOpenSearchClient;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OpenSearchConfiguration;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.service.util.mapper.CustomAuthorizedReportDefinitionDeserializer;
import io.camunda.optimize.service.util.mapper.CustomCollectionEntityDeserializer;
import io.camunda.optimize.service.util.mapper.CustomDefinitionDeserializer;
import io.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import io.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import io.camunda.optimize.service.util.mapper.CustomReportDefinitionDeserializer;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import io.camunda.search.connect.plugin.PluginConfiguration;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
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
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;

public class OpenSearchClientBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(OpenSearchClientBuilder.class);

  public static ExtendedOpenSearchClient buildOpenSearchClientFromConfig(
      final ConfigurationService configurationService, final PluginRepository pluginRepo) {
    final var plugins = extractPluginConfigs(configurationService.getOpenSearchConfiguration());
    LOG.trace("Loading plugins while building extended client: {}", plugins);
    pluginRepo.load(plugins);
    return new ExtendedOpenSearchClient(buildOpenSearchTransport(configurationService, pluginRepo));
  }

  public static OpenSearchAsyncClient buildOpenSearchAsyncClientFromConfig(
      final ConfigurationService configurationService, final PluginRepository pluginRepo) {
    final var plugins = extractPluginConfigs(configurationService.getOpenSearchConfiguration());
    LOG.trace("Loading plugins while building async client: {}", plugins);
    pluginRepo.load(plugins);
    return new OpenSearchAsyncClient(buildOpenSearchTransport(configurationService, pluginRepo));
  }

  private static List<PluginConfiguration> extractPluginConfigs(
      final OpenSearchConfiguration osConfig) {
    final var pluginConfigs = osConfig.getInterceptorPlugins();
    if (pluginConfigs != null) {
      return pluginConfigs.values().stream()
          .filter(plugin -> StringUtils.isNotBlank(plugin.id()))
          .toList();
    }
    return List.of();
  }

  private static OpenSearchTransport getAwsTransport(final ConfigurationService osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder().build();
    return new AwsSdk2Transport(
        httpClient,
        osConfig.getOpenSearchConfiguration().getFirstConnectionNode().getHost(),
        Region.of(region),
        AwsSdk2TransportOptions.builder()
            .setMapper(new JacksonJsonpMapper(ObjectMapperFactory.OPTIMIZE_MAPPER))
            .build());
  }

  public static RestClientBuilder buildDefaultRestClient(
      final ConfigurationService configurationService) {
    final RestClientBuilder restClientBuilder =
        RestClient.builder(buildOpenSearchConnectionNodesApache4(configurationService))
            .setRequestConfigCallback(
                requestConfigBuilder ->
                    requestConfigBuilder
                        .setConnectTimeout(
                            configurationService
                                .getOpenSearchConfiguration()
                                .getConnectionTimeout())
                        .setSocketTimeout(0));
    if (!StringUtils.isEmpty(configurationService.getOpenSearchConfiguration().getPathPrefix())) {
      restClientBuilder.setPathPrefix(
          configurationService.getOpenSearchConfiguration().getPathPrefix());
    }
    return restClientBuilder;
  }

  public static RestClient restClient(final ConfigurationService configurationService) {
    if (configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled()) {
      throw new NotSupportedException("Rest client is only supported with HTTP");
    } else {
      LOG.info("Setting up http rest client connection");
      return buildDefaultRestClient(configurationService).build();
    }
  }

  private static boolean useAwsCredentials(final ConfigurationService configurationService) {
    if (configurationService.getOpenSearchConfiguration().getConnection().getAwsEnabled()) {
      final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
      try {
        credentialsProvider.resolveCredentials();
        LOG.info("AWS Credentials can be resolved. Use AWS OpenSearch");
        return true;
      } catch (final Exception e) {
        LOG.info("Use standard OpenSearch since AWS not configured ({}) ", e.getMessage());
        return false;
      }
    }
    LOG.info("AWS Credentials are disabled. Using basic auth.");
    return false;
  }

  private static OpenSearchTransport buildOpenSearchTransport(
      final ConfigurationService configurationService, final PluginRepository pluginRepo) {
    if (useAwsCredentials(configurationService)) {
      return getAwsTransport(configurationService);
    }
    final HttpHost[] hosts = buildOpenSearchConnectionNodes(configurationService);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(hosts);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(
              httpClientBuilder, configurationService, pluginRepo.asRequestInterceptor());
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, configurationService);
          return requestConfigBuilder;
        });

    if (StringUtils.isNotBlank(configurationService.getOpenSearchConfiguration().getPathPrefix())) {
      builder.setPathPrefix(configurationService.getOpenSearchConfiguration().getPathPrefix());
    }

    builder.setMapper(new JacksonJsonpMapper(buildObjectMapper()));
    return builder.build();
  }

  private static JavaTimeModule buildJavaTimeModule() {
    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));

    javaTimeModule.addSerializer(
        Date.class, new DateSerializer(false, new StdDateFormat().withColonInTimeZone(false)));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));

    return javaTimeModule;
  }

  private static ObjectMapper buildObjectMapper() {
    final ObjectMapper mapper =
        Jackson2ObjectMapperBuilder.json()
            .modules(new Jdk8Module(), buildJavaTimeModule())
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY,
                SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS,
                SerializationFeature.INDENT_OUTPUT,
                DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .featuresToEnable(
                JsonParser.Feature.ALLOW_COMMENTS, MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    final SimpleModule module = new SimpleModule();
    module.addDeserializer(
        DefinitionOptimizeResponseDto.class, new CustomDefinitionDeserializer(mapper));
    module.addDeserializer(
        ReportDefinitionDto.class, new CustomReportDefinitionDeserializer(mapper));
    module.addDeserializer(
        AuthorizedReportDefinitionResponseDto.class,
        new CustomAuthorizedReportDefinitionDeserializer(mapper));
    module.addDeserializer(CollectionEntity.class, new CustomCollectionEntityDeserializer(mapper));
    mapper.registerModule(module);

    return mapper;
  }

  public static String getCurrentOSVersion(final OpenSearchClient osClient) throws IOException {
    // The request options is a placeholder for the eventual OpenSearch implementation
    return osClient.info().version().number();
  }

  private static HttpHost getHttpHost(
      final DatabaseConnectionNodeConfiguration configuration, final Boolean isSSLEnabled) {
    final String protocol = isSSLEnabled ? HTTPS_PREFIX : HTTP_PREFIX;
    final String uriConfig =
        String.format("%s%s:%d", protocol, configuration.getHost(), configuration.getHttpPort());
    try {
      final URI uri = new URI(uriConfig);
      return new org.apache.hc.core5.http.HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (final URISyntaxException e) {
      throw new OptimizeRuntimeException("Error in url: " + uriConfig, e);
    }
  }

  private static org.apache.http.HttpHost getHttpHostApache4(
      final DatabaseConnectionNodeConfiguration configuration, final Boolean isSSLEnabled) {
    final String protocol = isSSLEnabled ? HTTPS_PREFIX : HTTP_PREFIX;
    final String uriConfig =
        String.format("%s%s:%d", protocol, configuration.getHost(), configuration.getHttpPort());
    try {
      final URI uri = new URI(uriConfig);
      return new org.apache.http.HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    } catch (final URISyntaxException e) {
      throw new OptimizeRuntimeException("Error in url: " + uriConfig, e);
    }
  }

  private static HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final ConfigurationService configurationService) {
    final String username = configurationService.getOpenSearchConfiguration().getSecurityUsername();
    final String password = configurationService.getOpenSearchConfiguration().getSecurityPassword();
    if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
      LOG.warn(
          "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(
            getHttpHost(
                configurationService.getOpenSearchConfiguration().getFirstConnectionNode(),
                configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled())),
        new UsernamePasswordCredentials(username, password.toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private static void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ConfigurationService configurationService) {
    try {
      final ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      tlsStrategyBuilder.setSslContext(getSSLContext(configurationService));
      if (Boolean.TRUE.equals(
          configurationService.getOpenSearchConfiguration().getSkipHostnameVerification())) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      final TlsStrategy tlsStrategy = tlsStrategyBuilder.build();
      final PoolingAsyncClientConnectionManager connectionManager =
          PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);

    } catch (final Exception e) {
      LOG.error("Error in setting up SSLContext", e);
    }
  }

  private static SSLContext getSSLContext(final ConfigurationService configurationService)
      throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    final KeyStore truststore = loadCustomTrustStore(configurationService);
    final org.apache.http.ssl.TrustStrategy trustStrategy =
        Boolean.TRUE.equals(
            configurationService.getOpenSearchConfiguration().getSecuritySslSelfSigned())
            ? new TrustSelfSignedStrategy()
            : null;
    if (truststore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
    } else {
      // default if custom truststore is empty
      return SSLContext.getDefault();
    }
  }

  private static KeyStore loadCustomTrustStore(final ConfigurationService configurationService) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      // load custom es server certificate if configured
      setCertificateInTrustStore(trustStore, configurationService);

      return trustStore;
    } catch (final Exception e) {
      final String message =
          "Could not create certificate trustStore for the secured OpenSearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static void setCertificateInTrustStore(
      final KeyStore trustStore, final ConfigurationService configurationService) {
    final String serverCertificate =
        configurationService.getOpenSearchConfiguration().getSecuritySSLCertificate();
    if (serverCertificate != null) {
      try {
        final Certificate cert = loadCertificateFromPath(serverCertificate);
        trustStore.setCertificateEntry("opensearch-host", cert);
      } catch (final Exception e) {
        final String message =
            "Could not load configured server certificate for the secured OpenSearch Connection!";
        throw new OptimizeRuntimeException(message, e);
      }
    }

    // load trusted CA certificates
    int caCertificateCounter = 0;
    for (final String caCertificatePath :
        configurationService.getOpenSearchConfiguration().getSecuritySSLCertificateAuthorities()) {
      try {
        final Certificate cert = loadCertificateFromPath(caCertificatePath);
        trustStore.setCertificateEntry("custom-opensearch-ca-" + caCertificateCounter, cert);
        caCertificateCounter++;
      } catch (final Exception e) {
        final String message =
            "Could not load CA authority certificate for the secured OpenSearch Connection!";
        throw new OptimizeConfigurationException(message, e);
      }
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath)
      throws IOException, CertificateException {
    final Certificate cert;
    try (final BufferedInputStream bis =
        new BufferedInputStream(new FileInputStream(certificatePath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        LOG.debug("Found certificate: {}", cert);
      } else {
        throw new OptimizeRuntimeException(
            "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }

  private static HttpHost[] buildOpenSearchConnectionNodes(
      final ConfigurationService configurationService) {
    return configurationService.getOpenSearchConfiguration().getConnectionNodes().stream()
        .map(
            node ->
                getHttpHost(
                    node,
                    configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled()))
        .toArray(HttpHost[]::new);
  }

  private static org.apache.http.HttpHost[] buildOpenSearchConnectionNodesApache4(
      final ConfigurationService configurationService) {
    return configurationService.getOpenSearchConfiguration().getConnectionNodes().stream()
        .map(
            node ->
                getHttpHostApache4(
                    node,
                    configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled()))
        .toArray(org.apache.http.HttpHost[]::new);
  }

  private static HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ConfigurationService configurationService,
      final HttpRequestInterceptor... requestInterceptors) {
    setupAuthentication(httpAsyncClientBuilder, configurationService);

    for (final HttpRequestInterceptor interceptor : requestInterceptors) {
      httpAsyncClientBuilder.addRequestInterceptorLast(interceptor);
    }

    if (Boolean.TRUE.equals(
        configurationService.getOpenSearchConfiguration().getSecuritySSLEnabled())) {
      setupSSLContext(httpAsyncClientBuilder, configurationService);
    }
    return httpAsyncClientBuilder;
  }

  private static RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final ConfigurationService configurationService) {
    builder.setResponseTimeout(Timeout.ofMilliseconds(0));
    builder.setConnectionRequestTimeout(
        Timeout.ofMilliseconds(
            configurationService.getOpenSearchConfiguration().getConnectionTimeout()));
    builder.setConnectTimeout(
        Timeout.ofMilliseconds(
            configurationService.getOpenSearchConfiguration().getConnectionTimeout()));
    return builder;
  }
}
