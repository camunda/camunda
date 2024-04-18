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
package io.camunda.tasklist.webapp.security.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.Permission;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityAuthenticationTest {
  @Mock private Tokens tokens;
  @Mock private DecodedJWT decodedJWT;
  @Mock private AccessToken accessToken;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private Identity identity;
  @InjectMocks private IdentityAuthentication identityAuthentication;
  @Mock private ApplicationContext context;
  @Mock private Object details;
  private final long futureTime = System.currentTimeMillis() + 100000L;
  private final long pastTime = System.currentTimeMillis() - 100000L;

  @BeforeEach
  void setUp() {
    springContextHolder.setApplicationContext(context);
  }

  @Test
  public void getCredentialsShouldReturnAccessToken() {
    // given
    when(tokens.getAccessToken()).thenReturn("access_token");

    // when
    final String credentials = identityAuthentication.getCredentials();

    // then
    assertEquals("access_token", credentials);
  }

  @Test
  public void authenticatedShouldReturnTrueWhenAccessTokenNotExpired() {
    // when
    identityAuthentication.setAuthenticated(true);
    identityAuthentication.setExpires(new Date(futureTime));

    // then
    assertTrue(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticateShouldReturnFalseWhenRefreshTokenIsExpired() {
    // given
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));

    // then
    assertFalse(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticationShouldReturnTrueWhenAccessTokenExpiredButRefreshTokenValid() {
    // given
    identityAuthentication.setAuthenticated(true);

    ReflectionTestUtils.setField(
        identityAuthentication, "refreshTokenExpiresAt", new Date(futureTime));

    final List<String> permissionsList =
        Arrays.asList(
            PermissionConverter.READ_PERMISSION_VALUE, PermissionConverter.WRITE_PERMISSION_VALUE);
    identityAuthentication.setPermissions(permissionsList);
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));
    when(accessToken.getPermissions()).thenReturn(permissionsList);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getToken().getExpiresAt()).thenReturn(new Date(futureTime));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().decodeJWT(any()).getExpiresAt())
        .thenReturn(new Date(futureTime));
    // then
    assertTrue(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticationShouldReturnFalseWhenAccessTokenExpiredButRefreshTokenInvalid() {
    // given
    identityAuthentication.setAuthenticated(true);
    final List<String> permissionsList =
        Arrays.asList(
            PermissionConverter.READ_PERMISSION_VALUE, PermissionConverter.WRITE_PERMISSION_VALUE);
    identityAuthentication.setPermissions(permissionsList);

    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().decodeJWT(any()).getExpiresAt())
        .thenReturn(new Date(futureTime));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));
    when(accessToken.getPermissions()).thenReturn(permissionsList);
    when(accessToken.getToken().getExpiresAt()).thenReturn(new Date(pastTime));

    // then
    assertFalse(identityAuthentication.isAuthenticated());
  }

  @Test
  public void authenticationShouldReturnFalseWhenNoPermissions() {
    // given
    when(SpringContextHolder.getBean(Identity.class)).thenReturn(identity);
    when(identity.authentication()).thenReturn(mock(Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));

    // then
    assertFalse(identityAuthentication.isAuthenticated());
  }

  @Test
  public void getPermissionsShouldReturnListOfPermissions() {
    // given
    final List<Permission> expectedPermissions = Arrays.asList(Permission.READ, Permission.WRITE);
    identityAuthentication.setPermissions(
        List.of(
            PermissionConverter.READ_PERMISSION_VALUE, PermissionConverter.WRITE_PERMISSION_VALUE));

    // then
    assertEquals(expectedPermissions, identityAuthentication.getPermissions());
  }
}
