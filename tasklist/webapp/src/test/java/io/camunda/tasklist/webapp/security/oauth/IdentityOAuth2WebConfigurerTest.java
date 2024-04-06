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
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.webapp.security.oauth.IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.identity.sdk.IdentityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityOAuth2WebConfigurerTest {

  @Mock private Environment environment;

  @Mock private IdentityConfiguration identityConfiguration;

  @InjectMocks private IdentityOAuth2WebConfigurer webConfigurer;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void configureShouldEnableJWTWithSuccess() throws Exception {
    // given
    when(environment.containsProperty(
            IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI))
        .thenReturn(true);
    when(environment.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn("https://example.com/jwks");
    when(identityConfiguration.getIssuerBackendUrl()).thenReturn("https://example.com/identity");

    final HttpSecurity httpSecurity = mock(HttpSecurity.class);

    // when
    webConfigurer.configure(httpSecurity);

    // then
    verify(httpSecurity, times(1)).oauth2ResourceServer(any());
  }

  @Test
  public void configureJWTDisabledShouldNotApplyNoConfigurations() throws Exception {
    // given
    when(environment.containsProperty(
            IdentityOAuth2WebConfigurer.SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI))
        .thenReturn(false);
    when(environment.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn(false);

    final HttpSecurity httpSecurity = mock(HttpSecurity.class);

    // when
    webConfigurer.configure(httpSecurity);

    // then
    verify(httpSecurity, never()).oauth2ResourceServer(any());
  }

  @Test
  public void shouldReturnConcatUrlForJdkAuth() {
    when(identityConfiguration.getIssuerBackendUrl()).thenReturn("http://localhost:1111");

    final String result =
        (String) (ReflectionTestUtils.invokeGetterMethod(webConfigurer, "getJwkSetUriProperty"));

    assertThat(result).isEqualTo("http://localhost:1111/protocol/openid-connect/certs");
  }

  @Test
  public void shouldReturnConcatEnvVarJdkAuth() {
    when(environment.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn("http://localhost:1111");
    when(environment.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI))
        .thenReturn(true);

    final String result =
        (String) (ReflectionTestUtils.invokeGetterMethod(webConfigurer, "getJwkSetUriProperty"));

    assertThat(result).isEqualTo("http://localhost:1111");
  }
}
