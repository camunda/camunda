/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.qa.util.TestContainerUtil.KEYCLOAK_PASSWORD;
import static io.camunda.tasklist.qa.util.TestContainerUtil.KEYCLOAK_PASSWORD_2;
import static io.camunda.tasklist.qa.util.TestContainerUtil.KEYCLOAK_USERNAME;
import static io.camunda.tasklist.qa.util.TestContainerUtil.KEYCLOAK_USERNAME_2;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.impl.util.Environment;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.webapp.security.oauth.IdentityJwt2AuthenticationTokenConverter;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@ActiveProfiles({IDENTITY_AUTH_PROFILE, "tasklist", "test", "standalone"})
public abstract class IdentityTester extends SessionlessTasklistZeebeIntegrationTest {
  public static TestContext testContext;
  protected static final String USER = KEYCLOAK_USERNAME;
  protected static final String USER_2 = KEYCLOAK_USERNAME_2;
  private static final String REALM = "camunda-platform";
  private static final String CONTEXT_PATH = "/auth";
  private static final Map<String, String> USERS_STORE =
      Map.of(USER, KEYCLOAK_PASSWORD, USER_2, KEYCLOAK_PASSWORD_2);
  private static JwtDecoder jwtDecoder;
  @Autowired private static TestContainerUtil testContainerUtil;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private IdentityJwt2AuthenticationTokenConverter jwtAuthenticationConverter;

  protected static void beforeClass(final boolean multiTenancyEnabled) {

    testContainerUtil = new TestContainerUtil();
    testContext = new TestContext();
    testContainerUtil.startIdentity(
        testContext,
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.IDENTITY_CURRENTVERSION_DOCKER_PROPERTY_NAME),
        multiTenancyEnabled);
    jwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(
                testContext.getExternalKeycloakBaseUrl()
                    + "/auth/realms/camunda-platform/protocol/openid-connect/certs")
            .build();
    Environment.system().put("ZEEBE_CLIENT_ID", "zeebe");
    Environment.system().put("ZEEBE_CLIENT_SECRET", "zecret");
    Environment.system().put("ZEEBE_TOKEN_AUDIENCE", "zeebe-api");
    Environment.system()
        .put(
            "ZEEBE_AUTHORIZATION_SERVER_URL",
            testContext.getExternalKeycloakBaseUrl()
                + "/auth/realms/camunda-platform/protocol/openid-connect/token");

    /* Workaround: Zeebe Test Container is not yet compatible with CamundaClient. The deprecated ZeebeClient
    Environment properties must be set for the TestContainer poller.
    ref: https://camunda.slack.com/archives/CSQ2E3BT4/p1721717060291479?thread_ts=1721648856.848609&cid=CSQ2E3BT4 */
    io.camunda.zeebe.client.impl.util.Environment.system().put("ZEEBE_CLIENT_ID", "zeebe");
    io.camunda.zeebe.client.impl.util.Environment.system().put("ZEEBE_CLIENT_SECRET", "zecret");
    io.camunda.zeebe.client.impl.util.Environment.system().put("ZEEBE_TOKEN_AUDIENCE", "zeebe-api");
    io.camunda.zeebe.client.impl.util.Environment.system()
        .put(
            "ZEEBE_AUTHORIZATION_SERVER_URL",
            testContext.getExternalKeycloakBaseUrl()
                + "/auth/realms/camunda-platform/protocol/openid-connect/token");
  }

  @Override
  @BeforeEach
  public void before() {
    super.before();
    tester =
        beanFactory
            .getBean(TasklistTester.class, camundaClient, databaseTestExtension, jwtDecoder)
            .withAuthenticationToken(generateCamundaIdentityToken());
  }

  protected static void registerProperties(
      final DynamicPropertyRegistry registry, final boolean multiTenancyEnabled) {
    registry.add(
        TasklistProperties.PREFIX + ".identity.baseUrl",
        () -> testContext.getExternalIdentityBaseUrl());
    registry.add(TasklistProperties.PREFIX + ".identity.resourcePermissionsEnabled", () -> true);
    registry.add(
        TasklistProperties.PREFIX + ".identity.issuerBackendUrl",
        () -> testContext.getExternalKeycloakBaseUrl() + "/auth/realms/camunda-platform");
    registry.add(
        TasklistProperties.PREFIX + ".identity.issuerUrl",
        () -> testContext.getExternalKeycloakBaseUrl() + "/auth/realms/camunda-platform");
    registry.add(TasklistProperties.PREFIX + ".identity.clientId", () -> "tasklist");
    registry.add(TasklistProperties.PREFIX + ".identity.clientSecret", () -> "the-cake-is-alive");
    registry.add(TasklistProperties.PREFIX + ".identity.audience", () -> "tasklist-api");
    registry.add(TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup", () -> false);
    registry.add(TasklistProperties.PREFIX + ".archiver.rolloverEnabled", () -> false);
    registry.add(TasklistProperties.PREFIX + "importer.jobType", () -> "testJobType");
    registry.add(
        TasklistProperties.PREFIX + ".multiTenancy.enabled",
        () -> String.valueOf(multiTenancyEnabled));
  }

  protected String generateCamundaIdentityToken() {
    return generateToken(
        USER,
        KEYCLOAK_PASSWORD,
        "camunda-identity",
        testContainerUtil.getIdentityClientSecret(),
        "password",
        null);
  }

  protected String generateTasklistToken() {
    return generateToken(
        USER,
        KEYCLOAK_PASSWORD,
        "camunda-identity",
        testContainerUtil.getIdentityClientSecret(),
        "password",
        "tasklist-api");
  }

  protected String generateTokenForUser(final String username) {
    return generateToken(
        username,
        USERS_STORE.get(username),
        "camunda-identity",
        testContainerUtil.getIdentityClientSecret(),
        "password",
        null);
  }

  private String generateToken(final String clientId, final String clientSecret) {
    return generateToken(null, null, clientId, clientSecret, "client_credentials", null);
  }

  private String generateToken(
      final String defaultUserUsername,
      final String defaultUserPassword,
      final String clientId,
      final String clientSecret,
      final String grantType,
      final String audience) {
    final MultiValueMap<String, String> formValues = new LinkedMultiValueMap<>();
    formValues.put("grant_type", Collections.singletonList(grantType));
    formValues.put("client_id", Collections.singletonList(clientId));
    formValues.put("client_secret", Collections.singletonList(clientSecret));
    if (defaultUserUsername != null) {
      formValues.put("username", Collections.singletonList(defaultUserUsername));
    }
    if (defaultUserPassword != null) {
      formValues.put("password", Collections.singletonList(defaultUserPassword));
    }
    if (audience != null) {
      formValues.put("audience", Collections.singletonList(audience));
    }

    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    final RestTemplate restTemplate = new RestTemplate();
    final String tokenJson =
        restTemplate.postForObject(
            getAuthTokenUrl(), new HttpEntity<>(formValues, httpHeaders), String.class);
    try {
      return objectMapper.readTree(tokenJson).get("access_token").asText();
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getDemoUserId() {
    return getUserId(0);
  }

  protected String getUserId(final int index) {
    final String response = getUsers();
    try {
      return objectMapper.readTree(response).get(index).get("id").asText();
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getUsers() {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    httpHeaders.setBearerAuth(generateCamundaIdentityToken());
    final HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
    final RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<String> response =
        restTemplate.exchange(getUsersUrl(), HttpMethod.GET, entity, String.class);

    return response.getBody();
  }

  protected void createAuthorization(
      final String entityId,
      final String entityType,
      final String resourceKey,
      final String resourceType,
      final String permission) {
    final JsonObject obj =
        Json.createObjectBuilder()
            .add("entityId", entityId)
            .add("entityType", entityType)
            .add("resourceKey", resourceKey)
            .add("resourceType", resourceType)
            .add("permission", permission)
            .build();

    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    httpHeaders.setBearerAuth(generateCamundaIdentityToken());

    final RestTemplate restTemplate = new RestTemplate();

    final ResponseEntity<String> response =
        restTemplate.exchange(
            getAuthorizationsUrl(),
            HttpMethod.POST,
            new HttpEntity<>(obj.toString(), httpHeaders),
            String.class);
  }

  protected String getAuthTokenUrl() {
    return getAuthServerUrl()
        .concat("/realms/")
        .concat(REALM)
        .concat("/protocol/openid-connect/token");
  }

  protected String getUsersUrl() {
    return "http://"
        + testContext.getExternalIdentityHost()
        + ":"
        + testContext.getExternalIdentityPort()
        + "/api/users";
  }

  protected String getAuthorizationsUrl() {
    return "http://"
        + testContext.getExternalIdentityHost()
        + ":"
        + testContext.getExternalIdentityPort()
        + "/api/authorizations";
  }

  protected static String getAuthServerUrl() {
    return "http://"
        + testContext.getExternalKeycloakHost()
        + ":"
        + testContext.getExternalKeycloakPort()
        + CONTEXT_PATH;
  }

  @AfterAll
  public static void stopContainers() {
    Environment.system().remove("ZEEBE_CLIENT_ID");
    Environment.system().remove("ZEEBE_CLIENT_SECRET");
    Environment.system().remove("ZEEBE_TOKEN_AUDIENCE");
    Environment.system().remove("ZEEBE_AUTHORIZATION_SERVER_URL");
    Environment.system().remove("ZEEBE_CLIENT_ID");
    Environment.system().remove("ZEEBE_CLIENT_SECRET");
    Environment.system().remove("ZEEBE_TOKEN_AUDIENCE");
    Environment.system().remove("ZEEBE_AUTHORIZATION_SERVER_URL");
    testContainerUtil.stopIdentity(testContext);
  }
}
