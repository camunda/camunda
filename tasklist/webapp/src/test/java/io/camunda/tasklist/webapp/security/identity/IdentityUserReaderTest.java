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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.oauth.IdentityTenantAwareJwtAuthenticationToken;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityUserReaderTest {

  static final List<String> GROUPS = List.of("Group A", "Group B");
  @Mock private Identity identity;
  @Mock private ApplicationContext context;
  @Mock private AccessToken accessToken;
  @InjectMocks private SpringContextHolder springContextHolder;
  @Mock private DecodedJWT decodedJWT;
  @InjectMocks private IdentityUserReader identityUserReader;

  @BeforeEach
  void setUp() {
    springContextHolder.setApplicationContext(context);
  }

  @Test
  public void shouldReturnTheUserIdPermissionsAndGroupsByIdentityAuth() {
    // given
    final IdentityAuthentication identityAuthentication = mock(IdentityAuthentication.class);

    when(identityAuthentication.getId()).thenReturn("user123");
    when(identityAuthentication.getName()).thenReturn("userIdTest");
    when(identityAuthentication.getPermissions()).thenReturn(List.of(Permission.WRITE));
    when(identityAuthentication.getGroups()).thenReturn(GROUPS);

    // when
    final Optional<UserDTO> currentUser =
        identityUserReader.getCurrentUserBy(identityAuthentication);

    // then
    assertTrue(currentUser.isPresent());
    assertEquals("userIdTest", currentUser.get().getUserId());
    assertEquals(List.of(Permission.WRITE), currentUser.get().getPermissions());
    assertEquals(GROUPS, currentUser.get().getGroups());
  }

  @Test
  public void shouldReturnTheUserIdAndPermissionsAndGroupsJwtAuthenticationToken() {
    // given
    final Jwt jwt = mock(Jwt.class);
    final var jwtAuthenticationToken = mock(IdentityTenantAwareJwtAuthenticationToken.class);
    when(jwtAuthenticationToken.getName()).thenReturn("demo");
    when(jwtAuthenticationToken.getTenants()).thenReturn(Collections.emptyList());
    when(jwtAuthenticationToken.getPrincipal()).thenReturn(jwt);

    when(identity.authentication())
        .thenReturn(mock(io.camunda.identity.sdk.authentication.Authentication.class));
    when(identity.authentication().decodeJWT(any())).thenReturn(mock(DecodedJWT.class));
    when(identity.authentication().verifyToken(any())).thenReturn(accessToken);
    when(accessToken.getToken()).thenReturn(decodedJWT);
    when(accessToken.getUserDetails()).thenReturn(mock(UserDetails.class));

    // when
    final Optional<UserDTO> result = identityUserReader.getCurrentUserBy(jwtAuthenticationToken);

    // then
    assertThat(result)
        .isPresent()
        .contains(
            new UserDTO()
                .setUserId("demo")
                .setDisplayName("demo")
                .setApiUser(true)
                .setPermissions(Collections.emptyList())
                .setC8Links(Collections.emptyList())
                .setTenants(Collections.emptyList()));
  }

  @Test
  public void shouldReturnEmptyForOtherAuthentication() {
    // given
    final Authentication authentication =
        new UsernamePasswordAuthenticationToken("user123", "password");

    // when
    final Optional<UserDTO> currentUser = identityUserReader.getCurrentUserBy(authentication);

    // then
    assertFalse(currentUser.isPresent());
  }

  @Test
  public void shouldReturnExceptionWhenGettingToken() {
    final Jwt jwt = mock(Jwt.class);
    final var jwtAuthenticationToken = mock(IdentityTenantAwareJwtAuthenticationToken.class);

    assertThatThrownBy(() -> identityUserReader.getUserToken(jwtAuthenticationToken))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Get token is not supported for Identity authentication");
  }
}
