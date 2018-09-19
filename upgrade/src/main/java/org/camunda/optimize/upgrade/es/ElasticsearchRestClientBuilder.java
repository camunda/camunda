package org.camunda.optimize.upgrade.es;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class ElasticsearchRestClientBuilder {

  private static final String HTTP = "http";
  private static final String HTTPS = "https";

  public static RestClient build(ConfigurationService configurationService) {
    if (configurationService.getElasticsearchSecuritySSLEnabled()) {
      return buildSecuredRestClient(configurationService);
    }
    return buildDefaultRestClient(configurationService);
  }

  private static RestClient buildDefaultRestClient(ConfigurationService configurationService) {
    return RestClient.builder(
      new HttpHost(
        configurationService.getElasticSearchHost(),
        configurationService.getElasticSearchHttpPort(),
        HTTP
      )
    ).build();
  }

  private static RestClient buildSecuredRestClient(ConfigurationService configurationService) {
    try {
      RestClientBuilder builder = RestClient.builder(
        new HttpHost(
          configurationService.getElasticSearchHost(),
          configurationService.getElasticSearchHttpPort(),
          HTTPS
        ));

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

      return builder.build();
    } catch (Exception e) {
      String message = "Could not build ";
      throw new UpgradeRuntimeException(message, e);
    }
  }

  private static KeyStore loadKeystore(ConfigurationService configurationService) {
    try {
      //Put everything after here in your function.
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);//Make an empty store
      InputStream fis = new FileInputStream(configurationService.getElasticsearchSecuritySSLCertificate());
      BufferedInputStream bis = new BufferedInputStream(fis);

      CertificateFactory cf = CertificateFactory.getInstance("X.509");

      while (bis.available() > 0) {
        Certificate cert = cf.generateCertificate(bis);
        System.out.println("Certificate: " + cert);
        trustStore.setCertificateEntry("fiddler" + bis.available(), cert);
      }
      bis.close();
      return trustStore;
    } catch (Exception e) {
      String message = "Could not load certificate to connect against secured Elasticsearch!";
      throw new UpgradeRuntimeException(message, e);
    }
  }
}
