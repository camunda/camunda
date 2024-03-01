/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
                zeebeClient,
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
    } catch (JsonProcessingException e) {
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
