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

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Component
public class IdentityAuthorizationService {

  private final Logger logger = LoggerFactory.getLogger(IdentityAuthorizationService.class);

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private LocalValidatorFactoryBean defaultValidator;

  public List<String> getUserGroups() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String accessToken = null;
    final Identity identity = SpringContextHolder.getBean(Identity.class);
    // Extract access token based on authentication type
    if (authentication instanceof IdentityAuthentication) {
      accessToken = ((IdentityAuthentication) authentication).getTokens().getAccessToken();
      return identity.authentication().verifyToken(accessToken).getUserDetails().getGroups();
    } else if (authentication instanceof TokenAuthentication) {
      accessToken = ((TokenAuthentication) authentication).getAccessToken();
      final String organization = ((TokenAuthentication) authentication).getOrganization();
      return identity
          .authentication()
          .verifyToken(accessToken, organization)
          .getUserDetails()
          .getGroups();
    }

    // Fallback groups if authentication type is unrecognized or access token is null
    final List<String> defaultGroups = new ArrayList<>();
    defaultGroups.add(IdentityProperties.FULL_GROUP_ACCESS);
    return defaultGroups;
  }

  public boolean isAllowedToStartProcess(final String processDefinitionKey) {
    return !Collections.disjoint(
        getProcessDefinitionsFromAuthorization(),
        Set.of(IdentityProperties.ALL_RESOURCES, processDefinitionKey));
  }

  public List<String> getProcessReadFromAuthorization() {
    return getFromAuthorization(IdentityAuthorization.PROCESS_PERMISSION_READ);
  }

  public List<String> getProcessDefinitionsFromAuthorization() {
    return getFromAuthorization(IdentityAuthorization.PROCESS_PERMISSION_START);
  }

  private List<String> getFromAuthorization(final String type) {
    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()
        && tasklistProperties.getIdentity().getBaseUrl() != null) {
      final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof IdentityAuthentication) {
        final IdentityAuthentication identityAuthentication =
            (IdentityAuthentication) authentication;
        return switch (type) {
          case IdentityAuthorization.PROCESS_PERMISSION_READ ->
              identityAuthentication.getAuthorizations().getProcessesAllowedToRead();
          case IdentityAuthorization.PROCESS_PERMISSION_START ->
              identityAuthentication.getAuthorizations().getProcessesAllowedToStart();
          default -> null;
        };
      } else if (authentication instanceof JwtAuthenticationToken) {
        final JwtAuthenticationToken jwtAuthenticationToken =
            (JwtAuthenticationToken) authentication;
        final Identity identity = SpringContextHolder.getBean(Identity.class);

        return switch (type) {
          case IdentityAuthorization.PROCESS_PERMISSION_READ ->
              new IdentityAuthorization(
                      identity
                          .authorizations()
                          .forToken(jwtAuthenticationToken.getToken().getTokenValue()))
                  .getProcessesAllowedToRead();
          case IdentityAuthorization.PROCESS_PERMISSION_START ->
              new IdentityAuthorization(
                      identity
                          .authorizations()
                          .forToken(jwtAuthenticationToken.getToken().getTokenValue()))
                  .getProcessesAllowedToStart();
          default -> null;
        };

      } else if (authentication instanceof TokenAuthentication) {
        final Identity identity = SpringContextHolder.getBean(Identity.class);

        return switch (type) {
          case IdentityAuthorization.PROCESS_PERMISSION_READ ->
              new IdentityAuthorization(
                      identity
                          .authorizations()
                          .forToken(
                              ((TokenAuthentication) authentication).getAccessToken(),
                              ((TokenAuthentication) authentication).getOrganization()))
                  .getProcessesAllowedToRead();
          case IdentityAuthorization.PROCESS_PERMISSION_START ->
              new IdentityAuthorization(
                      identity
                          .authorizations()
                          .forToken(
                              ((TokenAuthentication) authentication).getAccessToken(),
                              ((TokenAuthentication) authentication).getOrganization()))
                  .getProcessesAllowedToStart();
          default -> null;
        };
      }
    }
    final List<String> result = new ArrayList<String>();
    result.add(IdentityProperties.ALL_RESOURCES);
    return result;
  }
}
