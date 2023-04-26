/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestContext;
import java.util.Collections;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public abstract class IdentityTester extends TasklistZeebeIntegrationTest {

  protected static TestContext testContext;
  private static final String REALM = "camunda-platform";
  private static final String CONTEXT_PATH = "/auth";
  private static final String USER = "demo";
  private static final String PASSWORD = "demo";
  @Autowired private static TestContainerUtil testContainerUtil;
  @Autowired private ObjectMapper objectMapper;

  @BeforeClass
  public static void beforeClass() {
    testContainerUtil = new TestContainerUtil();
    testContext = new TestContext();
    testContainerUtil.startIdentity(
        testContext,
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.IDENTITY_CURRENTVERSION_DOCKER_PROPERTY_NAME));
  }

  protected String generateCamundaIdentityToken() throws JsonProcessingException {
    return generateToken(
        USER, PASSWORD, "camunda-identity", "integration-tests-secret", "password", null);
  }

  protected String generateTasklistToken() throws JsonProcessingException {
    return generateToken(
        USER, PASSWORD, "camunda-identity", "integration-tests-secret", "password", "tasklist-api");
  }

  private String generateToken(String clientId, String clientSecret)
      throws JsonProcessingException {
    return generateToken(null, null, clientId, clientSecret, "client_credentials", null);
  }

  private String generateToken(
      final String defaultUserUsername,
      final String defaultUserPassword,
      final String clientId,
      final String clientSecret,
      final String grantType,
      final String audience)
      throws JsonProcessingException {
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
    return objectMapper.readTree(tokenJson).get("access_token").asText();
  }

  protected String getDemoUserId() throws JsonProcessingException {
    final String response = getUsers();
    return objectMapper.readTree(response).get(0).get("id").asText();
  }

  protected String getUsers() throws JsonProcessingException {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    httpHeaders.setBearerAuth(generateCamundaIdentityToken());
    final HttpEntity<String> entity = new HttpEntity<String>(httpHeaders);
    final RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<String> response =
        restTemplate.exchange(getUsersUrl(), HttpMethod.GET, entity, String.class);

    return response.getBody();
  }

  protected void createAuthorization(
      String entityId,
      String entityType,
      String resourceKey,
      String resourceType,
      String permission)
      throws JsonProcessingException, JSONException {
    final JSONObject obj = new JSONObject();

    obj.put("entityId", entityId);
    obj.put("entityType", entityType);
    obj.put("resourceKey", resourceKey);
    obj.put("resourceType", resourceType);
    obj.put("permission", permission);

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

  @AfterClass
  public static void stopContainers() {
    testContainerUtil.stopIdentity(testContext);
  }
}
