/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.MetricEnum.INDEXING_DURATION_METRIC;
import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static io.camunda.optimize.MetricEnum.OVERALL_IMPORT_TIME_METRIC;
import static io.camunda.optimize.OptimizeMetrics.METRICS_ENDPOINT;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.springframework.http.MediaType;

public class OptimizeRequestExecutor {

  private static final int MAX_LOGGED_BODY_SIZE = 10_000;
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OptimizeRequestExecutor.class);

  private final WebTarget defaultWebTarget;

  private WebTarget webTarget;

  private final String defaultUser;
  private final String defaultUserPassword;
  private final ObjectMapper objectMapper;
  private final Map<String, String> cookies = new HashMap<>();
  private final Map<String, String> requestHeaders = new HashMap<>();
  private String defaultAuthCookie;

  private String authCookie;
  private String path;
  private String method;
  private Entity<?> body;
  private String mediaType = MediaType.APPLICATION_JSON_VALUE;
  private Map<String, Object> queryParams;

  public OptimizeRequestExecutor(
      final String defaultUser, final String defaultUserPassword, final String restEndpoint) {
    this.defaultUser = defaultUser;
    this.defaultUserPassword = defaultUserPassword;
    objectMapper = getDefaultObjectMapper();
    defaultWebTarget = createWebTarget(restEndpoint);
    webTarget = defaultWebTarget;
  }

  public OptimizeRequestExecutor setActuatorWebTarget() {
    webTarget = createActuatorWebTarget();
    return this;
  }

  public OptimizeRequestExecutor initAuthCookie() {
    defaultAuthCookie = authenticateUserRequest(defaultUser, defaultUserPassword);
    authCookie = defaultAuthCookie;
    return this;
  }

  public OptimizeRequestExecutor addSingleQueryParam(final String key, final Object value) {
    if (queryParams != null && queryParams.size() != 0) {
      queryParams.put(key, value);
    } else {
      final HashMap<String, Object> params = new HashMap<>();
      params.put(key, value);
      queryParams = params;
    }
    return this;
  }

  public OptimizeRequestExecutor withoutAuthentication() {
    authCookie = null;
    return this;
  }

  public Response execute() {
    final Invocation.Builder builder = prepareRequest();

    final Response response;
    switch (method) {
      case GET:
        response = builder.get();
        break;
      case POST:
        response = builder.post(body);
        break;
      case PUT:
        response = builder.put(body);
        break;
      case DELETE:
        response = builder.delete();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported http method: " + method);
    }

    resetBuilder();
    // consume the response entity so the server can write the response
    response.bufferEntity();
    return response;
  }

  private Invocation.Builder prepareRequest() {
    WebTarget webTarget = this.webTarget.path(path);

    if (queryParams != null && queryParams.size() != 0) {
      for (final Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        if (queryParam.getValue() instanceof List) {
          for (final Object p : ((List) queryParam.getValue())) {
            webTarget =
                webTarget.queryParam(queryParam.getKey(), Objects.requireNonNullElse(p, "null"));
          }
        } else {
          webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
        }
      }
    }

    Invocation.Builder builder = webTarget.request();

    for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
      builder = builder.cookie(cookieEntry.getKey(), cookieEntry.getValue());
    }

    if (defaultAuthCookie == null) {
      initAuthCookie();
    }
    if (authCookie != null) {
      builder =
          builder.cookie(AuthCookieService.getAuthorizationCookieNameWithSuffix(0), authCookie);
    }

    for (final Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
    }
    return builder;
  }

  public <T> T execute(final Class<T> classToExtractFromResponse, final int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);
      return response.readEntity(classToExtractFromResponse);
    }
  }

  private void assertStatusCode(final Response response, final int expectedStatus) {
    final String responseString = response.readEntity(String.class);
    assertThat(response.getStatus())
        .withFailMessage(
            "Expected status code "
                + expectedStatus
                + ", actual status code: "
                + response.getStatus()
                + ".\nResponse contains the following message:\n"
                + responseString)
        .isEqualTo(expectedStatus);
  }

  private void resetBuilder() {
    webTarget = defaultWebTarget;
    authCookie = defaultAuthCookie;
    body = null;
    path = null;
    method = null;
    queryParams = null;
    mediaType = MediaType.APPLICATION_JSON_VALUE;
    cookies.clear();
    requestHeaders.clear();
  }

  public OptimizeRequestExecutor buildGetReadinessRequest() {
    path = "/readyz";
    method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildIndexingTimeMetricRequest() {
    path = METRICS_ENDPOINT + "/" + INDEXING_DURATION_METRIC.getName();
    method = GET;
    mediaType = MediaType.APPLICATION_JSON_VALUE;
    return this;
  }

  public OptimizeRequestExecutor buildPageFetchTimeMetricRequest() {
    path = METRICS_ENDPOINT + "/" + NEW_PAGE_FETCH_TIME_METRIC.getName();
    method = GET;
    mediaType = MediaType.APPLICATION_JSON_VALUE;
    return this;
  }

  public OptimizeRequestExecutor buildOverallImportTimeMetricRequest() {
    path = METRICS_ENDPOINT + "/" + OVERALL_IMPORT_TIME_METRIC.getName();
    method = GET;
    mediaType = MediaType.APPLICATION_JSON_VALUE;
    return this;
  }

  private String authenticateUserRequest(final String username, final String password) {
    final CredentialsRequestDto entity = new CredentialsRequestDto(username, password);
    final Response response =
        defaultWebTarget.path("authentication").request().post(Entity.json(entity));
    return AuthCookieService.createOptimizeAuthCookieValue(response.readEntity(String.class));
  }

  private WebTarget createActuatorWebTarget() {
    return createWebTarget(
        "http://localhost:"
            + OptimizeResourceConstants.ACTUATOR_PORT
            + OptimizeResourceConstants.ACTUATOR_ENDPOINT);
  }

  public WebTarget createWebTarget(final String targetUrl) {
    return createClient().target(targetUrl);
  }

  private Client createClient() {
    // register the default object provider for serialization/deserialization ob objects
    final OptimizeObjectMapperContextResolver provider =
        new OptimizeObjectMapperContextResolver(objectMapper);

    final Client client = ClientBuilder.newClient().register(provider);
    client.register(
        (ClientRequestFilter)
            requestContext ->
                LOG.debug(
                    "EmbeddedTestClient request {} {}",
                    requestContext.getMethod(),
                    requestContext.getUri()));
    client.register(
        (ClientResponseFilter)
            (requestContext, responseContext) -> {
              if (responseContext.hasEntity()) {
                responseContext.setEntityStream(
                    wrapEntityStreamIfNecessary(responseContext.getEntityStream()));
              }
              LOG.debug(
                  "EmbeddedTestClient response for {} {}: {}",
                  requestContext.getMethod(),
                  requestContext.getUri(),
                  responseContext.hasEntity()
                      ? serializeBodyCappedToMaxSize(responseContext.getEntityStream())
                      : "");
            });
    client.property(
        ClientProperties.CONNECT_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(
        ClientProperties.READ_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);

    acceptSelfSignedCertificates(client);
    return client;
  }

  private void acceptSelfSignedCertificates(final Client client) {
    try {
      // @formatter:off
      client
          .getSslContext()
          .init(
              null,
              new TrustManager[] {
                new X509TrustManager() {
                  @Override
                  public void checkClientTrusted(final X509Certificate[] arg0, final String arg1) {}

                  @Override
                  public void checkServerTrusted(final X509Certificate[] arg0, final String arg1) {}

                  @Override
                  public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                  }
                }
              },
              new java.security.SecureRandom());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
      // @formatter:on
    } catch (final KeyManagementException e) {
      throw new OptimizeIntegrationTestException(
          "Was not able to configure jersey client to accept all certificates", e);
    }
  }

  private InputStream wrapEntityStreamIfNecessary(final InputStream originalEntityStream) {
    return !originalEntityStream.markSupported()
        ? new BufferedInputStream(originalEntityStream)
        : originalEntityStream;
  }

  private String serializeBodyCappedToMaxSize(final InputStream entityStream) throws IOException {
    entityStream.mark(MAX_LOGGED_BODY_SIZE + 1);

    final byte[] entity = new byte[MAX_LOGGED_BODY_SIZE + 1];
    final int entitySize = entityStream.read(entity);
    final StringBuilder stringBuilder =
        new StringBuilder(
            new String(
                entity, 0, Math.min(entitySize, MAX_LOGGED_BODY_SIZE), StandardCharsets.UTF_8));
    if (entitySize > MAX_LOGGED_BODY_SIZE) {
      stringBuilder.append("...");
    }
    stringBuilder.append('\n');

    entityStream.reset();
    return stringBuilder.toString();
  }

  private static ObjectMapper getDefaultObjectMapper() {
    return OPTIMIZE_MAPPER;
  }

  public WebTarget getDefaultWebTarget() {
    return defaultWebTarget;
  }

  public WebTarget getWebTarget() {
    return webTarget;
  }
}
