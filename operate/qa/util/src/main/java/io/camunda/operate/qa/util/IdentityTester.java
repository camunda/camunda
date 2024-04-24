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
package io.camunda.operate.qa.util;

import static io.camunda.operate.StandaloneOperate.SPRING_THYMELEAF_PREFIX_KEY;
import static io.camunda.operate.StandaloneOperate.SPRING_THYMELEAF_PREFIX_VALUE;
import static io.camunda.operate.qa.util.TestContainerUtil.*;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;

import io.camunda.operate.property.OperateProperties;
import io.camunda.zeebe.client.impl.util.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;

public class IdentityTester {

  public static TestContext testContext;
  public static JwtDecoder jwtDecoder;
  public static TestContainerUtil testContainerUtil;
  protected static final String USER = KEYCLOAK_USERNAME;
  protected static final String USER_2 = KEYCLOAK_USERNAME_2;
  private static final String REALM = "camunda-platform";
  private static final String CONTEXT_PATH = "/auth";

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
    registry.add("camunda.identity.baseUrl", () -> testContext.getExternalIdentityBaseUrl());
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
    registry.add("camunda.operate.multiTenancy.enabled", () -> String.valueOf(multiTenancyEnabled));
  }
}
