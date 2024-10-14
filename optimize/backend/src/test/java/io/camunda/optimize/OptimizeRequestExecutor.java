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
import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.rest.GetVariableNamesForReportsRequestDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.jetty.OptimizeResourceConstants;
import io.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.client.ClientProperties;

@Slf4j
public class OptimizeRequestExecutor {

  private static final int MAX_LOGGED_BODY_SIZE = 10_000;

  @Getter private final WebTarget defaultWebTarget;

  @Getter private WebTarget webTarget;

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
  private String mediaType = MediaType.APPLICATION_JSON;
  private Map<String, Object> queryParams;

  public OptimizeRequestExecutor(
      final String defaultUser, final String defaultUserPassword, final String restEndpoint) {
    this.defaultUser = defaultUser;
    this.defaultUserPassword = defaultUserPassword;
    this.objectMapper = getDefaultObjectMapper();
    this.defaultWebTarget = createWebTarget(restEndpoint);
    this.webTarget = defaultWebTarget;
  }

  public OptimizeRequestExecutor setActuatorWebTarget() {
    this.webTarget = createActuatorWebTarget();
    return this;
  }

  public OptimizeRequestExecutor initAuthCookie() {
    this.defaultAuthCookie = authenticateUserRequest(defaultUser, defaultUserPassword);
    this.authCookie = defaultAuthCookie;
    return this;
  }

  public OptimizeRequestExecutor addSingleQueryParam(String key, Object value) {
    if (this.queryParams != null && queryParams.size() != 0) {
      this.queryParams.put(key, value);
    } else {
      HashMap<String, Object> params = new HashMap<>();
      params.put(key, value);
      this.queryParams = params;
    }
    return this;
  }

  public OptimizeRequestExecutor withoutAuthentication() {
    this.authCookie = null;
    return this;
  }

  public Response execute() {
    Invocation.Builder builder = prepareRequest();

    final Response response;
    switch (this.method) {
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
        throw new OptimizeIntegrationTestException("Unsupported http method: " + this.method);
    }

    resetBuilder();
    // consume the response entity so the server can write the response
    response.bufferEntity();
    return response;
  }

  private Invocation.Builder prepareRequest() {
    WebTarget webTarget = this.webTarget.path(this.path);

    if (queryParams != null && queryParams.size() != 0) {
      for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
        if (queryParam.getValue() instanceof List) {
          for (Object p : ((List) queryParam.getValue())) {
            webTarget =
                webTarget.queryParam(queryParam.getKey(), Objects.requireNonNullElse(p, "null"));
          }
        } else {
          webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
        }
      }
    }

    Invocation.Builder builder = webTarget.request();

    for (Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
      builder = builder.cookie(cookieEntry.getKey(), cookieEntry.getValue());
    }

    if (defaultAuthCookie == null) {
      initAuthCookie();
    }
    if (authCookie != null) {
      builder = builder.cookie(OPTIMIZE_AUTHORIZATION, this.authCookie);
    }

    for (Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
    }
    return builder;
  }

  public <T> T execute(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);
      return response.readEntity(classToExtractFromResponse);
    }
  }

  public <T> List<T> executeAndReturnList(Class<T> classToExtractFromResponse, int responseCode) {
    try (final Response response = execute()) {
      assertStatusCode(response, responseCode);

      String responseString = response.readEntity(String.class);
      TypeFactory factory = objectMapper.getTypeFactory();
      JavaType listOfT = factory.constructCollectionType(List.class, classToExtractFromResponse);
      return objectMapper.readValue(responseString, listOfT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void assertStatusCode(Response response, int expectedStatus) {
    String responseString = response.readEntity(String.class);
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
    this.webTarget = defaultWebTarget;
    this.authCookie = defaultAuthCookie;
    this.body = null;
    this.path = null;
    this.method = null;
    this.queryParams = null;
    this.mediaType = MediaType.APPLICATION_JSON;
    this.cookies.clear();
    this.requestHeaders.clear();
  }

  public OptimizeRequestExecutor buildGetReadinessRequest() {
    this.path = "/readyz";
    this.method = GET;
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesForReportsRequest(
      List<String> reportIds) {
    GetVariableNamesForReportsRequestDto requestDto = new GetVariableNamesForReportsRequestDto();
    requestDto.setReportIds(reportIds);
    this.path = "variables/reports";
    this.method = POST;
    this.body = getBody(requestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(
      ProcessVariableNameRequestDto variableRequestDto) {
    return buildProcessVariableNamesRequest(variableRequestDto, true);
  }

  public OptimizeRequestExecutor buildProcessVariableNamesRequest(
      ProcessVariableNameRequestDto variableRequestDto, boolean authenticationEnabled) {
    this.path = addExternalPrefixIfNeeded(authenticationEnabled) + "variables";
    this.method = POST;
    this.body = getBody(variableRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesForReportsRequest(
      ProcessVariableReportValuesRequestDto valuesRequestDto) {
    this.path = "variables/values/reports";
    this.method = POST;
    this.body = getBody(valuesRequestDto);
    return this;
  }

  public OptimizeRequestExecutor buildProcessVariableValuesRequest(
      ProcessVariableValueRequestDto valueRequestDto) {
    this.path = "variables/values";
    this.method = POST;
    this.body = getBody(valueRequestDto);
    return this;
  }

  @NotNull
  private String addExternalPrefixIfNeeded(final boolean authenticationEnabled) {
    return authenticationEnabled ? "" : "external/";
  }

  public OptimizeRequestExecutor buildIndexingTimeMetricRequest() {
    this.path = METRICS_ENDPOINT + "/" + INDEXING_DURATION_METRIC.getName();
    this.method = GET;
    this.mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  public OptimizeRequestExecutor buildPageFetchTimeMetricRequest() {
    this.path = METRICS_ENDPOINT + "/" + NEW_PAGE_FETCH_TIME_METRIC.getName();
    this.method = GET;
    this.mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  public OptimizeRequestExecutor buildOverallImportTimeMetricRequest() {
    this.path = METRICS_ENDPOINT + "/" + OVERALL_IMPORT_TIME_METRIC.getName();
    this.method = GET;
    this.mediaType = MediaType.APPLICATION_JSON;
    return this;
  }

  private Entity getBody(Object entity) {
    try {
      return entity == null
          ? Entity.entity("", mediaType)
          : Entity.entity(objectMapper.writeValueAsString(entity), mediaType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Couldn't serialize request" + e.getMessage(), e);
    }
  }

  private String authenticateUserRequest(String username, String password) {
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
    OptimizeObjectMapperContextResolver provider =
        new OptimizeObjectMapperContextResolver(objectMapper);

    Client client = ClientBuilder.newClient().register(provider);
    client.register(
        (ClientRequestFilter)
            requestContext ->
                log.debug(
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
              log.debug(
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
                  public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}

                  public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}

                  public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                  }
                }
              },
              new java.security.SecureRandom());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
      // @formatter:on
    } catch (KeyManagementException e) {
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
}
