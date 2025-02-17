/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.TestContainerUtil.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.operate.qa.util.IdentityTester;
import java.util.Collections;
import org.junit.Before;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public abstract class IdentityOperateZeebeAbstractIT extends OperateZeebeAbstractIT {

  protected static final String USER = KEYCLOAK_USERNAME;
  protected static final String USER_2 = KEYCLOAK_USERNAME_2;
  private static final String REALM = "camunda-platform";
  private static final String CONTEXT_PATH = "/auth";

  protected static String getAuthServerUrl() {
    return "http://"
        + IdentityTester.testContext.getExternalKeycloakHost()
        + ":"
        + IdentityTester.testContext.getExternalKeycloakPort()
        + CONTEXT_PATH;
  }

  @Override
  protected void mockTenantResponse() {
    // do not mock anything here
  }

  @Override
  @Before
  public void before() {
    super.before();
    tester =
        beanFactory
            .getBean(
                OperateTester.class,
                camundaClient,
                mockMvcTestRule,
                searchTestRule,
                IdentityTester.jwtDecoder)
            .withAuthenticationToken(generateCamundaIdentityToken());
  }

  protected String generateCamundaIdentityToken() {
    return generateToken(
        USER,
        KEYCLOAK_PASSWORD,
        "camunda-identity",
        IdentityTester.testContainerUtil.getIdentityClientSecret(),
        "password",
        null);
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

  protected String getAuthTokenUrl() {
    return getAuthServerUrl()
        .concat("/realms/")
        .concat(REALM)
        .concat("/protocol/openid-connect/token");
  }
}
