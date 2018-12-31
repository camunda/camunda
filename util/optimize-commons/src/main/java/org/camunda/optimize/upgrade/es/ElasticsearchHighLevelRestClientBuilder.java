package org.camunda.optimize.upgrade.es;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class ElasticsearchHighLevelRestClientBuilder {

  private static final String HTTP = "http";
  private static final String HTTPS = "https";

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchHighLevelRestClientBuilder.class);

  public static RestHighLevelClient build(ConfigurationService configurationService) {
    if (configurationService.getElasticsearchSecuritySSLEnabled()) {
      return buildSecuredRestClient(configurationService);
    }
    return buildDefaultRestClient(configurationService);
  }

  private static RestHighLevelClient buildDefaultRestClient(ConfigurationService configurationService) {
    RestClientBuilder clientBuilder = RestClient.builder(
      buildElasticsearchConnectionNodes(configurationService, HTTP)
    );
    return new RestHighLevelClient(clientBuilder);
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

  private static RestHighLevelClient buildSecuredRestClient(ConfigurationService configurationService) {
    try {
      RestClientBuilder builder = RestClient.builder(
        buildElasticsearchConnectionNodes(configurationService, HTTPS)
      );

      // enable encrypted communication
      KeyStore truststore = loadKeystore(configurationService);
      final SSLContext sslContext = SSLContexts.custom()
        .loadTrustMaterial(truststore, null)
        .build();

      // enable basic auth
      final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
          configurationService.getElasticsearchSecurityUsername(),
          configurationService.getElasticsearchSecurityPassword()
        )
      );

      builder
        .setHttpClientConfigCallback(
          httpClientBuilder -> {
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            httpClientBuilder.setSSLContext(sslContext);
            return httpClientBuilder;
          }
        );

      return new RestHighLevelClient(builder);
    } catch (Exception e) {
      String message = "Could not build ";
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private static KeyStore loadKeystore(ConfigurationService configurationService) {
    try {
      //Put everything after here in your function.
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);//Make an empty store
      FileInputStream fileInputStream =
        new FileInputStream(configurationService.getElasticsearchSecuritySSLCertificate());
      try (BufferedInputStream bis =
             new BufferedInputStream(fileInputStream)) {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        if (bis.available() > 0) {
          Certificate cert = cf.generateCertificate(bis);
          logger.debug("Found certificate: {}", cert);
          trustStore.setCertificateEntry("elasticsearch-" + bis.available(), cert);
        }
        return trustStore;
      }
    } catch (Exception e) {
      String message = "Could not load certificate to connect against secured Elasticsearch!";
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
