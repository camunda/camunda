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

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.InvalidClaimException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.property.MultiTenancyProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.tenant.TenantAwareAuthentication;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityJwt2AuthenticationTokenConverter.class,
      TasklistProperties.class
    },
    properties = {TasklistProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"})
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class IdentityJwt2AuthenticationTokenConverterTest {

  @Autowired @SpyBean private IdentityJwt2AuthenticationTokenConverter tokenConverter;

  @MockBean private Identity identity;
  @Mock private Authentication authentication;

  @MockBean private Tenants tenants;

  @SpyBean private TasklistProperties tasklistProperties;

  @Autowired private ApplicationContext applicationContext;

  @BeforeEach
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
  }

  @Test
  public void shouldFailIfClaimIsInvalid() {
    when(identity.authentication())
        .thenThrow(
            new InvalidClaimException(
                "The Claim 'aud' value doesn't contain the required audience."));
    final Jwt token = createJwtTokenWith();
    assertThrows(InsufficientAuthenticationException.class, () -> tokenConverter.convert(token));
  }

  @Test
  public void shouldFailIfTokenVerificationFails() {
    when(identity.authentication())
        .thenThrow(new RuntimeException("Any exception during token verification"));
    final Jwt token = createJwtTokenWith();
    assertThrows(InsufficientAuthenticationException.class, () -> tokenConverter.convert(token));
  }

  @Test
  public void shouldConvert() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isNotNull();
    assertThat(authenticationToken).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(authenticationToken.isAuthenticated()).isTrue();
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    // given
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    when(identity.tenants()).thenReturn(tenants);

    final var multiTenancyProperties = new MultiTenancyProperties().setEnabled(true);
    when(tasklistProperties.getMultiTenancy()).thenReturn(multiTenancyProperties);

    final List<Tenant> tenants =
        CommonUtils.OBJECT_MAPPER.readValue(
            this.getClass().getResource("/identity/tenants.json"), new TypeReference<>() {});
    when(this.tenants.forToken(any())).thenReturn(tenants);

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    // when
    final var result = tenantAwareAuth.getTenants();

    // then
    assertThat(result)
        .extracting("id", "name")
        .containsExactly(
            tuple("<default>", "Default"),
            tuple("tenant-a", "Tenant A"),
            tuple("tenant-b", "Tenant B"),
            tuple("tenant-c", "Tenant C"));
  }

  @Test
  public void shouldReturnEmptyTenantsListWhenMultiTenancyIsDisabled() {
    // given
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    when(identity.tenants()).thenReturn(tenants);

    final var multiTenancyProperties = new MultiTenancyProperties().setEnabled(false);
    when(tasklistProperties.getMultiTenancy()).thenReturn(multiTenancyProperties);

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);

    // when
    final var result = ((TenantAwareAuthentication) authenticationToken).getTenants();

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(tenants);
  }

  @Test
  public void shouldThrowExceptionWhen() {
    // given
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    when(identity.tenants()).thenReturn(tenants);

    final var multiTenancyProperties = new MultiTenancyProperties().setEnabled(true);
    when(tasklistProperties.getMultiTenancy()).thenReturn(multiTenancyProperties);

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;
    when(tenants.forToken(any())).thenThrow(new RestException("smth went wrong"));

    // when - then
    assertThatThrownBy(() -> tenantAwareAuth.getTenants())
        .isInstanceOf(InsufficientAuthenticationException.class)
        .hasMessage("smth went wrong");
  }

  protected Jwt createJwtTokenWith() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("foo", "bar")
        .build();
  }
}
