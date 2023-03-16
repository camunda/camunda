/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import org.camunda.optimize.plugin.PluginJarFileLoader;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ProxyConfiguration;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Optional;

public class ElasticsearchHighLevelRestClientBuilder {

  private static final String HTTP = "http";
  private static final String HTTPS = "https";
  private static RequestOptions requestOptions;

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchHighLevelRestClientBuilder.class);

  public static RestHighLevelClient build(final ConfigurationService configurationService) {
    requestOptions = getRequestOptions(configurationService);
    if (configurationService.getElasticsearchSecuritySSLEnabled()) {
      return buildHttpsRestClient(configurationService);
    }
    return buildHttpRestClient(configurationService);
  }

  public static String getCurrentESVersion(final RestHighLevelClient esClient,
                                           final RequestOptions requestOptions) throws IOException {
    final Request request = new Request(HttpGet.METHOD_NAME, "/");
    request.setOptions(requestOptions);
    final String responseJson = EntityUtils.toString(esClient.getLowLevelClient().performRequest(request).getEntity());
    ObjectNode node = new ObjectMapper().readValue(responseJson, ObjectNode.class);
    return node.get("version").get("number").toString().replace("\"", "");
  }

  private static RestHighLevelClient buildHttpRestClient(final ConfigurationService configurationService) {
    logger.info("Setting up http rest client connection");
    return getRestHighLevelClient(buildDefaultRestClient(configurationService, HTTP), requestOptions);
  }

  private static RestHighLevelClient buildHttpsRestClient(final ConfigurationService configurationService) {
    logger.info("Setting up https rest client connection");
    try {
      final RestClientBuilder builder = buildDefaultRestClient(configurationService, HTTPS);

      final SSLContext sslContext;
      final KeyStore truststore = loadCustomTrustStore(configurationService);

      if (truststore.size() > 0) {
        final TrustStrategy trustStrategy = configurationService.getElasticsearchSecuritySslSelfSigned() == Boolean.TRUE ?
          new TrustSelfSignedStrategy() : null;
        sslContext = SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
      } else {
        // default if custom truststore is empty
        sslContext = SSLContext.getDefault();
      }

      builder.setHttpClientConfigCallback(createHttpClientConfigCallback(configurationService, sslContext));
      return getRestHighLevelClient(builder, requestOptions);
    } catch (Exception e) {
      String message = "Could not build secured Elasticsearch client.";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static RestClientBuilder.HttpClientConfigCallback createHttpClientConfigCallback(
    final ConfigurationService configurationService) {
    return createHttpClientConfigCallback(configurationService, null);
  }

  private static RestClientBuilder.HttpClientConfigCallback createHttpClientConfigCallback(
    final ConfigurationService configurationService,
    final SSLContext sslContext) {
    return httpClientBuilder -> {
      buildCredentialsProviderIfConfigured(configurationService)
        .ifPresent(httpClientBuilder::setDefaultCredentialsProvider);

      httpClientBuilder.setSSLContext(sslContext);

      final ProxyConfiguration proxyConfig = configurationService.getElasticsearchProxyConfig();
      if (proxyConfig.isEnabled()) {
        httpClientBuilder.setProxy(new HttpHost(
          proxyConfig.getHost(), proxyConfig.getPort(), proxyConfig.isSslEnabled() ? "https" : "http"
        ));
      }

      if (configurationService.getElasticsearchSkipHostnameVerification()) {
        // setting this to always be true essentially skips the hostname verification
        httpClientBuilder.setSSLHostnameVerifier((s, sslSession) -> true);
      }

      return httpClientBuilder;
    };
  }

  private static RestClientBuilder buildDefaultRestClient(ConfigurationService configurationService, String protocol) {
    final RestClientBuilder restClientBuilder = RestClient.builder(
        buildElasticsearchConnectionNodes(configurationService, protocol))
      .setRequestConfigCallback(
        requestConfigBuilder -> requestConfigBuilder
          .setConnectTimeout(configurationService.getElasticsearchConnectionTimeout())
          .setSocketTimeout(0)
      );
    if (!StringUtils.isEmpty(configurationService.getElasticsearchPathPrefix())) {
      restClientBuilder.setPathPrefix(configurationService.getElasticsearchPathPrefix());
    }

    restClientBuilder.setHttpClientConfigCallback(createHttpClientConfigCallback(configurationService));

    return restClientBuilder;
  }

  private static HttpHost[] buildElasticsearchConnectionNodes(ConfigurationService configurationService,
                                                              String protocol) {
    return configurationService.getElasticsearchConnectionNodes()
      .stream()
      .map(conf -> new HttpHost(
             conf.getHost(),
             conf.getHttpPort(),
             protocol
           )
      )
      .toArray(HttpHost[]::new);
  }

  private static Optional<CredentialsProvider> buildCredentialsProviderIfConfigured(
    final ConfigurationService configurationService) {
    CredentialsProvider credentialsProvider = null;
    if (configurationService.getElasticsearchSecurityUsername() != null
      && configurationService.getElasticsearchSecurityPassword() != null) {
      credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
          configurationService.getElasticsearchSecurityUsername(),
          configurationService.getElasticsearchSecurityPassword()
        )
      );
    } else {
      logger.debug("Elasticsearch username and password not provided, skipping connection credential setup.");
    }
    return Optional.ofNullable(credentialsProvider);
  }

  private static KeyStore loadCustomTrustStore(ConfigurationService configurationService) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);

      // load custom es server certificate if configured
      final String serverCertificate = configurationService.getElasticsearchSecuritySSLCertificate();
      if (serverCertificate != null) {
        try {
          Certificate cert = loadCertificateFromPath(serverCertificate);
          trustStore.setCertificateEntry("elasticsearch-host", cert);
        } catch (Exception e) {
          String message = "Could not load configured server certificate for the secured Elasticsearch Connection!";
          throw new OptimizeConfigurationException(message, e);
        }
      }

      // load trusted CA certificates
      int caCertificateCounter = 0;
      for (String caCertificatePath : configurationService.getElasticsearchSecuritySSLCertificateAuthorities()) {
        try {
          Certificate cert = loadCertificateFromPath(caCertificatePath);
          trustStore.setCertificateEntry("custom-elasticsearch-ca-" + caCertificateCounter, cert);
          caCertificateCounter++;
        } catch (Exception e) {
          String message = "Could not load CA authority certificate for the secured Elasticsearch Connection!";
          throw new OptimizeConfigurationException(message, e);
        }
      }

      return trustStore;
    } catch (Exception e) {
      String message = "Could not create certificate trustStore for the secured Elasticsearch Connection!";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath)
    throws IOException, CertificateException {
    Certificate cert;
    final FileInputStream fileInputStream = new FileInputStream(certificatePath);
    try (BufferedInputStream bis = new BufferedInputStream(fileInputStream)) {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        logger.debug("Found certificate: {}", cert);
      } else {
        throw new OptimizeConfigurationException(
          "Could not load certificate from file, file is empty. File: " + certificatePath
        );
      }
    }
    return cert;
  }

  private static RestHighLevelClient getRestHighLevelClient(final RestClientBuilder builder,
                                                            final RequestOptions requestOptions) {
    RestHighLevelClient restHighLevelClient = new RestHighLevelClientBuilder(builder.build())
      .build();
    try {
      // we only need to turn on the compatibility mode when the server is ES8
      final String esVersion = getCurrentESVersion(restHighLevelClient, requestOptions);
      if (esVersion.charAt(0) == '8') {
        restHighLevelClient = new RestHighLevelClientBuilder(builder.build())
          .setApiCompatibilityMode(true)
          .build();
        logger.info("The ES client has now enabled its compatibility mode for ES8.");
      }
      logger.info("Finished setting up HTTP rest client connection.");
      return restHighLevelClient;
    } catch (IOException e) {
      String message = "Could not fetch the version of the Elasticsearch server.";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static RequestOptions getRequestOptions(final ConfigurationService configurationService) {
    ElasticsearchCustomHeaderProvider customHeaderProvider = new ElasticsearchCustomHeaderProvider(configurationService, new PluginJarFileLoader(configurationService));
    customHeaderProvider.initPlugins();
    RequestOptionsProvider requestOptionsProvider = new RequestOptionsProvider(customHeaderProvider.getPlugins(), configurationService);
    return requestOptionsProvider.getRequestOptions();
  }

}
