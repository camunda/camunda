/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.connect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.elasticsearch.ExtendedElasticSearchClient;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.SslProperties;
import io.camunda.operate.util.RetryOperation;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Conditional(ElasticsearchCondition.class)
@Configuration
public class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired private OperateProperties operateProperties;

  private ElasticsearchClient elasticsearchClient;

  public static void closeEsClient(RestHighLevelClient esClient) {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        LOGGER.error("Could not close esClient", e);
      }
    }
  }

  public static void closeEsClient(ElasticsearchClient esClient) {
    if (esClient != null) {
      esClient.shutdown();
    }
  }

  @Bean
  public ElasticsearchClient elasticsearchClient() {
    LOGGER.debug("Creating ElasticsearchClient ...");
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final RestClientBuilder restClientBuilder = RestClient.builder(getHttpHost(elsConfig));
    if (elsConfig.getConnectTimeout() != null || elsConfig.getSocketTimeout() != null) {
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setTimeouts(configCallback, elsConfig));
    }
    final RestClient restClient =
        restClientBuilder
            .setHttpClientConfigCallback(
                httpClientBuilder -> configureHttpClient(httpClientBuilder, elsConfig))
            .build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    // And create the API client
    elasticsearchClient = new ElasticsearchClient(transport);
    if (!checkHealth(elasticsearchClient)) {
      LOGGER.warn("Elasticsearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return elasticsearchClient;
  }

  public boolean checkHealth(ElasticsearchClient elasticsearchClient) {
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
    } catch (Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  @Bean("esClient")
  public ExtendedElasticSearchClient esClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createEsClient(operateProperties.getElasticsearch());
  }

  @Bean("zeebeEsClient")
  public ExtendedElasticSearchClient zeebeEsClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createEsClient(operateProperties.getZeebeElasticsearch());
  }

  @PreDestroy
  public void tearDown() {
    if (elasticsearchClient != null) {
      try {
        elasticsearchClient._transport().close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public ExtendedElasticSearchClient createEsClient(ElasticsearchProperties elsConfig) {
    LOGGER.debug("Creating Elasticsearch connection...");
    final RestClientBuilder restClientBuilder =
        RestClient.builder(getHttpHost(elsConfig))
            .setHttpClientConfigCallback(
                httpClientBuilder -> configureHttpClient(httpClientBuilder, elsConfig));
    if (elsConfig.getConnectTimeout() != null || elsConfig.getSocketTimeout() != null) {
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setTimeouts(configCallback, elsConfig));
    }
    final ExtendedElasticSearchClient esClient =
        new ExtendedElasticSearchClient(restClientBuilder, true);
    if (!checkHealth(esClient)) {
      LOGGER.warn("Elasticsearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return esClient;
  }

  private HttpAsyncClientBuilder configureHttpClient(
      HttpAsyncClientBuilder httpAsyncClientBuilder, ElasticsearchProperties elsConfig) {
    setupAuthentication(httpAsyncClientBuilder, elsConfig);
    if (elsConfig.getSsl() != null) {
      setupSSLContext(httpAsyncClientBuilder, elsConfig.getSsl());
    }
    return httpAsyncClientBuilder;
  }

  private void setupSSLContext(
      HttpAsyncClientBuilder httpAsyncClientBuilder, SslProperties sslConfig) {
    try {
      httpAsyncClientBuilder.setSSLContext(getSSLContext(sslConfig));
      if (!sslConfig.isVerifyHostname()) {
        httpAsyncClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }
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
          "Could not create certificate trustStore for the secured Elasticsearch Connection!";
      throw new OperateRuntimeException(message, e);
    }
  }

  private void setCertificateInTrustStore(
      final KeyStore trustStore, final String serverCertificate) {
    try {
      final Certificate cert = loadCertificateFromPath(serverCertificate);
      trustStore.setCertificateEntry("elasticsearch-host", cert);
    } catch (Exception e) {
      final String message =
          "Could not load configured server certificate for the secured Elasticsearch Connection!";
      throw new OperateRuntimeException(message, e);
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

  private HttpHost getHttpHost(ElasticsearchProperties elsConfig) {
    try {
      final URI uri = new URI(elsConfig.getUrl());
      return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    } catch (URISyntaxException e) {
      throw new OperateRuntimeException("Error in url: " + elsConfig.getUrl(), e);
    }
  }

  private void setupAuthentication(
      final HttpAsyncClientBuilder builder, ElasticsearchProperties elsConfig) {
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

  public boolean checkHealth(RestHighLevelClient esClient) {
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
    } catch (Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }
}
