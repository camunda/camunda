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

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.oauth.IdentityTenantAwareJwtAuthenticationToken;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityUserReader implements UserReader {

  @Autowired private Identity identity;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication instanceof IdentityAuthentication identityAuthentication) {
      return Optional.of(
          new UserDTO()
              // For testing assignee migration locally use 'identityAuthentication.getId()'
              .setUserId(/*identityAuthentication.getId()*/ identityAuthentication.getName())
              .setDisplayName(identityAuthentication.getUserDisplayName())
              .setPermissions(identityAuthentication.getPermissions())
              .setTenants(identityAuthentication.getTenants())
              .setGroups(identityAuthentication.getGroups()));
    } else if (authentication
        instanceof IdentityTenantAwareJwtAuthenticationToken identityTenantAwareToken) {
      final AccessToken accessToken =
          identity
              .authentication()
              .verifyToken(((Jwt) identityTenantAwareToken.getPrincipal()).getTokenValue());
      final List<Permission> permissions =
          accessToken.getPermissions().stream()
              .map(PermissionConverter.getInstance()::convert)
              .collect(Collectors.toList());

      final String userDisplayName =
          accessToken.getUserDetails().getName().orElse(identityTenantAwareToken.getName());

      return Optional.of(
          new UserDTO()
              .setUserId(identityTenantAwareToken.getName())
              .setDisplayName(userDisplayName)
              .setApiUser(true)
              .setPermissions(permissions)
              .setTenants(identityTenantAwareToken.getTenants()));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return map(usernames, name -> new UserDTO().setUserId(name).setDisplayName(name));
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Identity authentication");
  }
}
