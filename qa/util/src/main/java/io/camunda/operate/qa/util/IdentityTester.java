/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.property.OperateProperties;
import io.camunda.zeebe.client.impl.util.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;

import static io.camunda.operate.Application.SPRING_THYMELEAF_PREFIX_KEY;
import static io.camunda.operate.Application.SPRING_THYMELEAF_PREFIX_VALUE;
import static io.camunda.operate.qa.util.TestContainerUtil.*;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;

public class IdentityTester {

  public static TestContext testContext;

  protected static final String USER = KEYCLOAK_USERNAME;
  protected static final String USER_2 = KEYCLOAK_USERNAME_2;
  private static final String REALM = "camunda-platform";
  private static final String CONTEXT_PATH = "/auth";

  public static JwtDecoder jwtDecoder;

  public static TestContainerUtil testContainerUtil;

  public static void startIdentityBeforeTestClass(boolean multiTenancyEnabled) {

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
  }

  public static void registerProperties(
      DynamicPropertyRegistry registry, boolean multiTenancyEnabled) {
    registry.add(
        "camunda.identity.baseUrl", () -> testContext.getExternalIdentityBaseUrl());
//    registry.add("camunda.operate.identity.resourcePermissionsEnabled", () -> true);
    registry.add(
        "camunda.identity.issuerBackendUrl",
        () -> testContext.getExternalKeycloakBaseUrl() + "/auth/realms/camunda-platform");
    registry.add(
        "camunda.identity.issuerUrl",
        () -> testContext.getExternalKeycloakBaseUrl() + "/auth/realms/camunda-platform");
    registry.add("camunda.identity.clientId", () -> "tasklist");
    registry.add("camunda.identity.clientSecret", () -> "the-cake-is-alive");
    registry.add("camunda.identity.audience", () -> "tasklist-api");
    registry.add("server.servlet.session.cookie.name", () -> COOKIE_JSESSIONID);
    registry.add(OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup", () -> false);
    registry.add(OperateProperties.PREFIX + ".archiver.rolloverEnabled", () -> false);
    registry.add(OperateProperties.PREFIX + "importer.jobType", () -> "testJobType");
    registry.add("graphql.servlet.exception-handlers-enabled", () -> true);
    registry.add(
        "management.endpoints.web.exposure.include", () -> "info,prometheus,loggers,usage-metrics");
    registry.add(SPRING_THYMELEAF_PREFIX_KEY, () -> SPRING_THYMELEAF_PREFIX_VALUE);
    registry.add("server.servlet.session.cookie.name", () -> COOKIE_JSESSIONID);
    registry.add(
        "camunda.operate.multiTenancy.enabled", () -> String.valueOf(multiTenancyEnabled));
  }

}
