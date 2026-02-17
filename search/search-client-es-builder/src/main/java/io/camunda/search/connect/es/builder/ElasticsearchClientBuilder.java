/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es.builder;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified builder for creating Elasticsearch clients. Consolidates client creation logic that was
 * previously duplicated across 5 separate connectors (search-client-connect, Tasklist, Operate,
 * Optimize, and Zeebe Exporter).
 *
 * <p>The builder supports all configuration options found across those connectors:
 *
 * <ul>
 *   <li>Connection: single/multiple URLs
 *   <li>Authentication: basic auth (username/password)
 *   <li>SSL/TLS: configured via {@link SslConfig} (X.509 PEM, PKCS12, CA certs, self-signed trust,
 *       hostname verification)
 *   <li>Proxy: configured via {@link ProxyConfig} (HTTP/HTTPS proxy with optional preemptive
 *       authentication)
 *   <li>Timeouts: connect timeout, socket timeout
 *   <li>ES compatibility: configurable compatibility headers (compatible-with=N)
 *   <li>HTTP tuning: IO thread count
 *   <li>Path prefix: for reverse proxy setups
 *   <li>Interceptors: custom HTTP request interceptors
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * ElasticsearchClient client = ElasticsearchClientBuilder.newInstance()
 *     .withUrl("http://localhost:9200")
 *     .withBasicAuth("elastic", "changeme")
 *     .withObjectMapper(objectMapper)
 *     .build();
 * }</pre>
 *
 * <p>All clients automatically include ES9 compatibility headers (compatible-with=9) on every
 * request.
 */
public final class ElasticsearchClientBuilder {

  /**
   * The compatibility version sent via Accept/Content-Type headers. All clients always send {@code
   * compatible-with=9} to ensure correct communication with Elasticsearch.
   */
  static final int COMPATIBILITY_VERSION = 9;

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchClientBuilder.class);

  // Connection
  private final List<String> urls = new ArrayList<>();
  private boolean urlSetterCalled;
  private boolean urlsSetterCalled;

  // Authentication
  private String username;
  private String password;

  // SSL/TLS
  private SslConfig sslConfig;

  // Proxy
  private ProxyConfig proxyConfig;

  // Timeouts
  private Integer connectTimeoutMs;
  private Integer socketTimeoutMs;

  // HTTP tuning
  private Integer ioThreadCount;

  // Path prefix
  private String pathPrefix;

  // Jackson
  private ObjectMapper objectMapper;

  // Transport options
  private TransportOptions transportOptions;

  // Interceptors
  private final List<HttpRequestInterceptor> interceptors = new ArrayList<>();

  private ElasticsearchClientBuilder() {}

  /** Creates a new builder instance. */
  public static ElasticsearchClientBuilder newInstance() {
    return new ElasticsearchClientBuilder();
  }

  // ── Connection ──

  /**
   * Sets a single Elasticsearch URL (e.g., "http://localhost:9200"). Mutually exclusive with {@link
   * #withUrls(List)}.
   *
   * @throws ElasticsearchClientBuilderException if {@link #withUrls(List)} was already called
   */
  public ElasticsearchClientBuilder withUrl(final String url) {
    if (urlsSetterCalled) {
      throw new ElasticsearchClientBuilderException(
          "Cannot call withUrl() after withUrls() — use one or the other, not both");
    }
    urlSetterCalled = true;
    urls.clear();
    if (url != null) {
      urls.add(url);
    }
    return this;
  }

  /**
   * Sets multiple Elasticsearch URLs. Mutually exclusive with {@link #withUrl(String)}.
   *
   * @throws ElasticsearchClientBuilderException if {@link #withUrl(String)} was already called
   */
  public ElasticsearchClientBuilder withUrls(final List<String> urls) {
    if (urlSetterCalled) {
      throw new ElasticsearchClientBuilderException(
          "Cannot call withUrls() after withUrl() — use one or the other, not both");
    }
    urlsSetterCalled = true;
    this.urls.clear();
    if (urls != null) {
      this.urls.addAll(urls);
    }
    return this;
  }

  // ── Authentication ──

  /** Configures basic authentication with username and password. */
  public ElasticsearchClientBuilder withBasicAuth(final String username, final String password) {
    this.username = username;
    this.password = password;
    return this;
  }

  // ── SSL/TLS ──

  /** Configures SSL/TLS settings via an {@link SslConfig}. */
  public ElasticsearchClientBuilder withSslConfig(final SslConfig sslConfig) {
    this.sslConfig = sslConfig;
    return this;
  }

  // ── Proxy ──

  /** Configures proxy settings via a {@link ProxyConfig}. */
  public ElasticsearchClientBuilder withProxyConfig(final ProxyConfig proxyConfig) {
    this.proxyConfig = proxyConfig;
    return this;
  }

  // ── Timeouts ──

  /** Sets the connection timeout in milliseconds. */
  public ElasticsearchClientBuilder withConnectTimeout(final Integer millis) {
    connectTimeoutMs = millis;
    return this;
  }

  /**
   * Sets the socket timeout in milliseconds. Use 0 for infinite (no timeout), as used by the
   * Optimize connector.
   */
  public ElasticsearchClientBuilder withSocketTimeout(final Integer millis) {
    socketTimeoutMs = millis;
    return this;
  }

  // ── HTTP Tuning ──

  /**
   * Sets the number of IO threads for the async HTTP client. The Zeebe exporter uses 1 thread for
   * minimal resource usage.
   *
   * @param count the number of IO threads
   */
  public ElasticsearchClientBuilder withIoThreadCount(final int count) {
    ioThreadCount = count;
    return this;
  }

  // ── Path Prefix ──

  /**
   * Sets a path prefix for all requests. Used by Optimize for reverse proxy setups.
   *
   * @param prefix the path prefix (e.g., "/elasticsearch")
   */
  public ElasticsearchClientBuilder withPathPrefix(final String prefix) {
    pathPrefix = prefix;
    return this;
  }

  // ── Jackson ──

  /** Sets a custom {@link ObjectMapper} for JSON serialization/deserialization. */
  public ElasticsearchClientBuilder withObjectMapper(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  // ── Transport Options ──

  /**
   * Sets custom {@link TransportOptions} to be applied to the Elasticsearch client. Used by
   * Optimize for response consumer buffer limits.
   */
  public ElasticsearchClientBuilder withTransportOptions(final TransportOptions transportOptions) {
    this.transportOptions = transportOptions;
    return this;
  }

  // ── Interceptors ──

  /** Adds one or more HTTP request interceptors (appended last, after plugin interceptors). */
  public ElasticsearchClientBuilder withRequestInterceptors(
      final HttpRequestInterceptor... interceptors) {
    for (final HttpRequestInterceptor interceptor : interceptors) {
      if (interceptor != null) {
        this.interceptors.add(interceptor);
      }
    }
    return this;
  }

  // ── Build ──

  /**
   * Builds and returns an {@link ElasticsearchClient}.
   *
   * @return a configured ElasticsearchClient
   */
  public ElasticsearchClient build() {
    final var restClient = buildRestClient();
    final var mapper = resolveObjectMapper();
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
    return transportOptions != null
        ? new ElasticsearchClient(transport, transportOptions)
        : new ElasticsearchClient(transport);
  }

  /**
   * Builds and returns an {@link ElasticsearchAsyncClient}.
   *
   * @return a configured ElasticsearchAsyncClient
   */
  public ElasticsearchAsyncClient buildAsync() {
    final var restClient = buildRestClient();
    final var mapper = resolveObjectMapper();
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
    return transportOptions != null
        ? new ElasticsearchAsyncClient(transport, transportOptions)
        : new ElasticsearchAsyncClient(transport);
  }

  /**
   * Builds and returns the low-level {@link RestClient}. This is useful for consumers that need
   * direct access to the REST client (e.g., Optimize which maintains both a high-level and
   * low-level client).
   *
   * @return a configured RestClient
   */
  public RestClient buildRestClient() {
    if (urls.isEmpty()) {
      throw new ElasticsearchClientBuilderException(
          "At least one Elasticsearch URL must be configured");
    }

    final HttpHost[] httpHosts = parseUrls();
    final RestClientBuilder restClientBuilder = RestClient.builder(httpHosts);

    // Timeouts
    if (connectTimeoutMs != null || socketTimeoutMs != null) {
      restClientBuilder.setRequestConfigCallback(this::configureTimeouts);
    }

    // Compatibility headers — always sent
    final String headerValue =
        "application/vnd.elasticsearch+json;compatible-with=" + COMPATIBILITY_VERSION;
    final Header[] defaultHeaders =
        new Header[] {
          new BasicHeader("Accept", headerValue), new BasicHeader("Content-Type", headerValue)
        };
    restClientBuilder.setDefaultHeaders(defaultHeaders);

    // Path prefix
    if (pathPrefix != null && !pathPrefix.isEmpty()) {
      restClientBuilder.setPathPrefix(pathPrefix);
    }

    // HTTP client configuration
    restClientBuilder.setHttpClientConfigCallback(this::configureHttpClient);

    return restClientBuilder.build();
  }

  // ── Internal: URL parsing ──

  private HttpHost[] parseUrls() {
    return urls.stream()
        .map(
            url -> {
              try {
                return HttpHost.create(url);
              } catch (final Exception e) {
                throw new ElasticsearchClientBuilderException(
                    "Invalid Elasticsearch URL: " + url, e);
              }
            })
        .toArray(HttpHost[]::new);
  }

  // ── Internal: Timeouts ──

  private RequestConfig.Builder configureTimeouts(final RequestConfig.Builder builder) {
    if (connectTimeoutMs != null) {
      builder.setConnectTimeout(connectTimeoutMs);
    }
    if (socketTimeoutMs != null) {
      builder.setSocketTimeout(socketTimeoutMs);
    }
    return builder;
  }

  // ── Internal: HTTP client configuration ──

  private HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder) {
    // IO thread count
    if (ioThreadCount != null) {
      httpAsyncClientBuilder.setDefaultIOReactorConfig(
          IOReactorConfig.custom().setIoThreadCount(ioThreadCount).build());
    }

    // Basic authentication
    configureAuthentication(httpAsyncClientBuilder);

    // SSL/TLS
    if (sslConfig != null) {
      configureSsl(httpAsyncClientBuilder);
    }

    // Request interceptors (plugin interceptors, custom interceptors)
    for (final HttpRequestInterceptor interceptor : interceptors) {
      httpAsyncClientBuilder.addInterceptorLast(interceptor);
    }

    // Proxy
    if (proxyConfig != null) {
      configureProxy(httpAsyncClientBuilder);
    }

    return httpAsyncClientBuilder;
  }

  // ── Internal: Authentication ──

  private void configureAuthentication(final HttpAsyncClientBuilder builder) {
    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      if (username != null || password != null) {
        LOGGER.warn(
            "Username and/or password are empty. Basic authentication for Elasticsearch is not"
                + " used.");
      }
      return;
    }

    final var credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  // ── Internal: SSL/TLS ──

  private void configureSsl(final HttpAsyncClientBuilder builder) {
    if (!sslConfig.isEnabled()) {
      return;
    }

    try {
      final SSLContext sslContext = buildSslContext();
      builder.setSSLContext(sslContext);

      if (!sslConfig.isVerifyHostname()) {
        builder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }
    } catch (final ElasticsearchClientBuilderException e) {
      throw e;
    } catch (final Exception e) {
      throw new ElasticsearchClientBuilderException("Failed to configure SSL/TLS", e);
    }
  }

  private SSLContext buildSslContext() throws Exception {
    final KeyStore trustStore = loadTrustStore();

    final var trustStrategy = sslConfig.isSelfSigned() ? new TrustSelfSignedStrategy() : null;

    if (trustStore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(trustStore, trustStrategy).build();
    } else {
      return SSLContext.getDefault();
    }
  }

  private KeyStore loadTrustStore() {
    try {
      final String certPath = sslConfig.getCertificatePath();

      // PKCS12 keystore (from Tasklist connector)
      if (certPath != null && (certPath.endsWith(".p12") || certPath.endsWith(".pfx"))) {
        return loadPkcs12KeyStore(certPath);
      }

      // Build a custom trust store with X.509 certificates
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);

      // Load server certificate
      if (certPath != null) {
        final Certificate cert = loadCertificateFromPath(certPath);
        trustStore.setCertificateEntry("elasticsearch-host", cert);
      }

      // Load CA certificates (from Optimize connector)
      final List<String> caList = sslConfig.getCertificateAuthorities();
      for (int i = 0; i < caList.size(); i++) {
        final Certificate caCert = loadCertificateFromPath(caList.get(i));
        trustStore.setCertificateEntry("custom-elasticsearch-ca-" + i, caCert);
      }

      return trustStore;
    } catch (final ElasticsearchClientBuilderException e) {
      throw e;
    } catch (final Exception e) {
      throw new ElasticsearchClientBuilderException(
          "Could not create certificate trust store for the secured Elasticsearch connection", e);
    }
  }

  private static KeyStore loadPkcs12KeyStore(final String certPath) {
    try (final FileInputStream fis = new FileInputStream(certPath)) {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(fis, null);
      return keyStore;
    } catch (final Exception e) {
      throw new ElasticsearchClientBuilderException(
          "Could not load PKCS12 certificate from path: " + certPath, e);
    }
  }

  private static Certificate loadCertificateFromPath(final String certPath)
      throws IOException, CertificateException {
    try (final BufferedInputStream bis = new BufferedInputStream(new FileInputStream(certPath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");
      if (bis.available() > 0) {
        final Certificate cert = cf.generateCertificate(bis);
        LOGGER.debug("Loaded certificate: {}", cert);
        return cert;
      } else {
        throw new ElasticsearchClientBuilderException("Certificate file is empty: " + certPath);
      }
    }
  }

  // ── Internal: Proxy ──

  private void configureProxy(final HttpAsyncClientBuilder builder) {
    final String host = proxyConfig.getHost();
    final int port = proxyConfig.getPort();

    if (host == null || host.trim().isEmpty()) {
      throw new ElasticsearchClientBuilderException(
          "Proxy is configured but no proxy host is specified");
    }

    if (port <= 0 || port > 65_535) {
      throw new ElasticsearchClientBuilderException(
          "Proxy port must be between 1 and 65535, but was: " + port);
    }

    builder.setProxy(new HttpHost(host, port, proxyConfig.isSslEnabled() ? "https" : "http"));

    // Preemptive proxy authentication
    if (proxyConfig.hasAuth()) {
      final String credentials = proxyConfig.getUsername() + ":" + proxyConfig.getPassword();
      final String encoded =
          Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      final String proxyAuthHeaderValue = "Basic " + encoded;

      builder.addInterceptorFirst(
          (HttpRequestInterceptor)
              (request, context) -> {
                if (!request.containsHeader("Proxy-Authorization")) {
                  request.addHeader("Proxy-Authorization", proxyAuthHeaderValue);
                }
              });

      LOGGER.debug("Preemptive proxy authentication enabled");
    }
  }

  // ── Internal: ObjectMapper ──

  private ObjectMapper resolveObjectMapper() {
    return objectMapper != null ? objectMapper : new ObjectMapper();
  }
}
