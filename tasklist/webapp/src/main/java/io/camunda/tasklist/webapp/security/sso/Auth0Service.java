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

import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_CALLBACK;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.sso.model.ClusterInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Profile(TasklistProfileService.SSO_AUTH_PROFILE)
public class Auth0Service {
  private static final Logger LOGGER = LoggerFactory.getLogger(Auth0Service.class);
  private static final String LOGOUT_URL_TEMPLATE = "https://%s/v2/logout?client_id=%s&returnTo=%s";
  private static final String PERMISSION_URL_TEMPLATE = "%s/%s";

  private static final List<String> SCOPES =
      List.of(
          "openid", "profile", "email", "offline_access" // request refresh token
          );

  @Autowired private BeanFactory beanFactory;

  @Autowired private AuthenticationController authenticationController;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  public Authentication authenticate(final HttpServletRequest req, final HttpServletResponse res) {
    final Tokens tokens = retrieveTokens(req, res);
    final TokenAuthentication authentication = beanFactory.getBean(TokenAuthentication.class);
    authentication.authenticate(
        tokens.getIdToken(), tokens.getRefreshToken(), tokens.getAccessToken());
    checkPermission(authentication);
    return authentication;
  }

  private void checkPermission(final TokenAuthentication authentication) {
    final HttpHeaders headers = new HttpHeaders();

    headers.setBearerAuth(authentication.getAccessToken());
    final String urlDomain = tasklistProperties.getCloud().getPermissionUrl();
    final String url =
        String.format(
            PERMISSION_URL_TEMPLATE, urlDomain, tasklistProperties.getAuth0().getOrganization());
    final ResponseEntity<ClusterInfo> responseEntity =
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(headers), ClusterInfo.class);

    final ClusterInfo clusterInfo = responseEntity.getBody();
    if (clusterInfo.getSalesPlan() != null) {
      authentication.setSalesPlanType(clusterInfo.getSalesPlan().getType());
    }

    final ClusterInfo.Permission tasklistPermissions =
        clusterInfo.getPermissions().getCluster().getTasklist();
    if (tasklistPermissions.getRead()) {
      authentication.addPermission(Permission.READ);
    } else {
      throw new InsufficientAuthenticationException("User doesn't have read access");
    }

    if (tasklistPermissions.getDelete()
        && tasklistPermissions.getCreate()
        && tasklistPermissions.getUpdate()) {
      authentication.addPermission(Permission.WRITE);
    }
  }

  public String getAuthorizeUrl(final HttpServletRequest req, final HttpServletResponse res) {
    return authenticationController
        .buildAuthorizeUrl(req, res, getRedirectURI(req, SSO_CALLBACK, true))
        .withAudience(tasklistProperties.getCloud().getPermissionAudience())
        .withScope(String.join(" ", SCOPES))
        .build();
  }

  public String getLogoutUrlFor(final String returnTo) {
    return String.format(
        LOGOUT_URL_TEMPLATE,
        tasklistProperties.getAuth0().getDomain(),
        tasklistProperties.getAuth0().getClientId(),
        returnTo);
  }

  public Tokens retrieveTokens(final HttpServletRequest req, final HttpServletResponse res) {
    final String operationName = "retrieve tokens";
    final RetryPolicy<Tokens> retryPolicy =
        new RetryPolicy<Tokens>()
            .handle(IdentityVerificationException.class)
            .withDelay(Duration.ofMillis(500))
            .withMaxAttempts(10)
            .onRetry(e -> LOGGER.debug("Retrying #{} {}", e.getAttemptCount(), operationName))
            .onAbort(e -> LOGGER.error("Abort {} by {}", operationName, e.getFailure()))
            .onRetriesExceeded(
                e ->
                    LOGGER.error("Retries {} exceeded for {}", e.getAttemptCount(), operationName));
    return Failsafe.with(retryPolicy).get(() -> authenticationController.handle(req, res));
  }

  public String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    return getRedirectURI(req, redirectTo, false);
  }

  public String getRedirectURI(
      final HttpServletRequest req, final String redirectTo, boolean omitContextPath) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80)
        || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    final String clusterId = req.getContextPath().replace("/", "");
    if (omitContextPath) {
      return redirectUri + redirectTo + "?uuid=" + clusterId;
    } else {
      return redirectUri + req.getContextPath() + redirectTo;
    }
  }
}
