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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.zeebeimport.M2mTokenManager.FIELD_NAME_ACCESS_TOKEN;
import static io.camunda.operate.zeebeimport.M2mTokenManager.FIELD_NAME_AUDIENCE;
import static io.camunda.operate.zeebeimport.M2mTokenManager.FIELD_NAME_CLIENT_ID;
import static io.camunda.operate.zeebeimport.M2mTokenManager.FIELD_NAME_CLIENT_SECRET;
import static io.camunda.operate.zeebeimport.M2mTokenManager.FIELD_NAME_GRANT_TYPE;
import static io.camunda.operate.zeebeimport.M2mTokenManager.GRANT_TYPE_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebeimport.util.TestApplicationWithNoBeans;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      M2mTokenManager.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class
    },
    properties = {
      "camunda.operate.auth0.domain=" + M2mTokenManagerIT.AUTH0_DOMAIN,
      "camunda.operate.auth0.m2mClientId=" + M2mTokenManagerIT.M2M_CLIENT_ID,
      "camunda.operate.auth0.m2mClientSecret=" + M2mTokenManagerIT.M2M_CLIENT_SECRET,
      "camunda.operate.auth0.m2mAudience=" + M2mTokenManagerIT.M2M_AUDIENCE,
    })
public class M2mTokenManagerIT {

  protected static final String AUTH0_DOMAIN = "auth0.domain";
  protected static final String M2M_CLIENT_ID = "clientId";
  protected static final String M2M_CLIENT_SECRET = "clientSecret";
  protected static final String M2M_AUDIENCE = "audience";
  private final String mockJwtToken =
      JWT.create()
          .withExpiresAt(new Date(Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli()))
          .sign(Algorithm.HMAC256("secret"));
  @Autowired @InjectMocks private M2mTokenManager m2mTokenManager;

  @MockBean
  @Qualifier("incidentNotificationRestTemplate")
  private RestTemplate restTemplate;

  @Before
  public void setup() {}

  @After
  public void cleanup() {
    m2mTokenManager.clearCache();
  }

  @Test
  public void testGetTokenFromCache() {
    given(restTemplate.postForEntity(anyString(), any(Object.class), any(Class.class)))
        .willReturn(
            new ResponseEntity<Map>(asMap(FIELD_NAME_ACCESS_TOKEN, mockJwtToken), HttpStatus.OK));

    // when requesting the token for the 1st time
    String token = m2mTokenManager.getToken();

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    assertAuth0IsRequested(1);

    // when requesting the token for the 2nd time
    token = m2mTokenManager.getToken();

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    // no request to Auth0 is sent
    verifyNoMoreInteractions(restTemplate);
  }

  @Test
  public void testTokenWithForceUpdate() {
    given(restTemplate.postForEntity(anyString(), any(Object.class), any(Class.class)))
        .willReturn(
            new ResponseEntity<Map>(asMap(FIELD_NAME_ACCESS_TOKEN, mockJwtToken), HttpStatus.OK));

    // when
    // requesting for the 1st time
    m2mTokenManager.getToken();
    // requesting with forceUpdate = true
    String token = m2mTokenManager.getToken(true);

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    // rest call was sent twice
    assertAuth0IsRequested(2);

    // when requesting with forceUpdate = false
    token = m2mTokenManager.getToken(false);

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    // no request to Auth0 is sent
    verifyNoMoreInteractions(restTemplate);
  }

  @Test
  public void testTokenIsExpired() {
    // given
    final String mockExpiredJwtToken =
        JWT.create()
            .withExpiresAt(new Date(Instant.now().minus(10, ChronoUnit.MINUTES).toEpochMilli()))
            .sign(Algorithm.HMAC256("secret"));
    given(restTemplate.postForEntity(anyString(), any(Object.class), any(Class.class)))
        .willReturn(
            new ResponseEntity<Map>(
                asMap(FIELD_NAME_ACCESS_TOKEN, mockExpiredJwtToken), HttpStatus.OK),
            new ResponseEntity<Map>(asMap(FIELD_NAME_ACCESS_TOKEN, mockJwtToken), HttpStatus.OK));
    // cache the expired token
    m2mTokenManager.getToken();
    clearInvocations(restTemplate);

    // when asking for token again
    final String token = m2mTokenManager.getToken();
    assertAuth0IsRequested(1);
    assertThat(token).isEqualTo(mockJwtToken);
  }

  private void assertAuth0IsRequested(final int times) {
    // assert request to Auth0
    final ArgumentCaptor<ObjectNode> tokenRequestCaptor = ArgumentCaptor.forClass(ObjectNode.class);
    final ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<Class> responseTypeCaptor = ArgumentCaptor.forClass(Class.class);
    verify(restTemplate, times(times))
        .postForEntity(
            urlCaptor.capture(), tokenRequestCaptor.capture(), responseTypeCaptor.capture());
    assertThat(urlCaptor.getValue())
        .isEqualTo(String.format("https://%s/oauth/token", AUTH0_DOMAIN));
    final ObjectNode value = tokenRequestCaptor.getValue();
    assertThat(value.get(FIELD_NAME_GRANT_TYPE).asText()).isEqualTo(GRANT_TYPE_VALUE);
    assertThat(value.get(FIELD_NAME_CLIENT_ID).asText()).isEqualTo(M2M_CLIENT_ID);
    assertThat(value.get(FIELD_NAME_CLIENT_SECRET).asText()).isEqualTo(M2M_CLIENT_SECRET);
    assertThat(value.get(FIELD_NAME_AUDIENCE).asText()).isEqualTo(M2M_AUDIENCE);
    assertThat(responseTypeCaptor.getValue()).isEqualTo(Map.class);
  }
}
