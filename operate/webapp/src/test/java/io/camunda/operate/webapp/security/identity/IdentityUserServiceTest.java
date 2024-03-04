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
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.sso.TokenAuthentication;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
public class IdentityUserServiceTest {

  private IdentityUserService underTest;

  @Mock private Identity mockIdentity;

  @Mock private PermissionConverter mockPermissionConverter;

  @BeforeEach
  public void setup() {
    underTest = new IdentityUserService(mockIdentity, mockPermissionConverter);
  }

  @Test
  public void testCreateUserDtoFromIdentityAuthentication() {
    var identityAuthentication = Mockito.mock(IdentityAuthentication.class);

    List<Permission> authPermissions = Arrays.asList(Permission.fromString("READ"));
    List<OperateTenant> authTenants = Arrays.asList(new OperateTenant("tenantId", "tenantName"));

    when(identityAuthentication.getId()).thenReturn("mockId");
    when(identityAuthentication.getName()).thenReturn("mockName");
    when(identityAuthentication.getTenants()).thenReturn(authTenants);
    when(identityAuthentication.getPermissions()).thenReturn(authPermissions);

    UserDto result = underTest.createUserDtoFrom(identityAuthentication);

    // Validate the DTO object was created with the expected fields
    assertThat(result).isNotNull();
    assertThat(result.getDisplayName()).isEqualTo(identityAuthentication.getName());
    assertThat(result.isCanLogout()).isTrue();
    assertThat(result.getPermissions()).isEqualTo(authPermissions);
    assertThat(result.getTenants()).isEqualTo(authTenants);
  }

  @Test
  public void testCreateUserDtoFromJwtToken() {
    var jwtToken = Mockito.mock(JwtAuthenticationToken.class);
    var mockJwt = Mockito.mock(Jwt.class);
    var mockAuthentication = Mockito.mock(Authentication.class);
    var mockAccessToken = Mockito.mock(AccessToken.class);

    when(jwtToken.getPrincipal()).thenReturn(mockJwt);
    when(jwtToken.getName()).thenReturn("mockTokenName");
    when(mockJwt.getTokenValue()).thenReturn("mockTokenValue");
    when(mockIdentity.authentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.verifyToken(any())).thenReturn(mockAccessToken);
    when(mockAccessToken.getPermissions())
        .thenReturn(Arrays.asList(PermissionConverter.READ_PERMISSION_VALUE));
    when(mockPermissionConverter.convert(any())).thenCallRealMethod();

    UserDto result = underTest.createUserDtoFrom(jwtToken);

    // Validate the DTO object was created with the expected fields
    assertThat(result).isNotNull();
    assertThat(result.getDisplayName()).isEqualTo(jwtToken.getName());
    assertThat(result.getUserId()).isEqualTo(jwtToken.getName());
    assertThat(result.isCanLogout()).isEqualTo(true);

    List<Permission> permissions = result.getPermissions();
    assertThat(permissions).isNotNull();
    assertThat(permissions.size()).isEqualTo(1);
    assertThat(permissions.get(0)).isEqualTo(Permission.READ);
  }

  @Test
  public void testCreateDtoUserFromInvalidType() {
    var tokenAuthentication = Mockito.mock(TokenAuthentication.class);

    UserDto result = underTest.createUserDtoFrom(tokenAuthentication);

    assertThat(result).isNull();
  }

  @Test
  public void testCreateDtoUserFromNullType() {
    assertThat(underTest.createUserDtoFrom(null)).isNull();
  }
}
