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
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import com.auth0.jwt.interfaces.Claim;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.graphql.entity.C8AppLink;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.sso.model.C8ConsoleService;
import io.camunda.tasklist.webapp.security.sso.model.ClusterMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile(SSO_AUTH_PROFILE)
public class SSOUserReader implements UserReader {

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private C8ConsoleService c8ConsoleService;

  @Autowired private IdentityAuthorizationService identityAuthorizationService;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication instanceof TokenAuthentication) {
      final TokenAuthentication tokenAuthentication = (TokenAuthentication) authentication;
      final Map<String, Claim> claims = tokenAuthentication.getClaims();
      String name = DEFAULT_USER;
      if (claims.containsKey(tasklistProperties.getAuth0().getNameKey())) {
        name = claims.get(tasklistProperties.getAuth0().getNameKey()).asString();
      }
      final String email = claims.get(tasklistProperties.getAuth0().getEmailKey()).asString();
      final ClusterMetadata clusterMetadata = c8ConsoleService.getClusterMetadata();
      List<C8AppLink> c8Links = List.of();
      if (clusterMetadata != null) {
        c8Links = clusterMetadata.getUrlsAsC8AppLinks();
      }
      return Optional.of(
          new UserDTO()
              // For testing assignee migration locally use 'authentication.getName()'
              .setUserId(/*authentication.getName()*/ email)
              .setDisplayName(name)
              .setApiUser(false)
              .setGroups(identityAuthorizationService.getUserGroups())
              .setPermissions(tokenAuthentication.getPermissions())
              .setRoles(
                  tokenAuthentication.getRoles(tasklistProperties.getAuth0().getOrganizationsKey()))
              .setSalesPlanType(tokenAuthentication.getSalesPlanType())
              .setC8Links(c8Links));
    } else if (authentication instanceof JwtAuthenticationToken) {
      final JwtAuthenticationToken jwtAuthentication = ((JwtAuthenticationToken) authentication);
      final String name =
          jwtAuthentication.getName() == null ? DEFAULT_USER : jwtAuthentication.getName();
      return Optional.of(
          new UserDTO()
              .setUserId(name)
              .setDisplayName(name)
              .setApiUser(true)
              // M2M token in the cloud always has WRITE permissions
              .setPermissions(List.of(Permission.WRITE)));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return tasklistProperties.getAuth0().getOrganization();
  }

  @Override
  public List<UserDTO> getUsersByUsernames(List<String> usernames) {
    return map(
        usernames, name -> new UserDTO().setDisplayName(name).setUserId(name).setApiUser(false));
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    if (authentication instanceof TokenAuthentication) {
      return Optional.of(
          JSONObject.valueToString(
              ((TokenAuthentication) authentication).getNewTokenByRefreshToken()));
    } else {
      throw new UnsupportedOperationException(
          "Not supported for token class: " + authentication.getClass().getName());
    }
  }
}
