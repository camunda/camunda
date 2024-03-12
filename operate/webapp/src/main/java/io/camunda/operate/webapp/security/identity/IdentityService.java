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

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.IDENTITY_CALLBACK_URI;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.SecurityContextWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityService {

  private final OperateProperties operateProperties;

  private final Identity identity;

  private final IdentityRetryService identityRetryService;

  private final SecurityContextWrapper securityContextWrapper;

  @Autowired
  public IdentityService(
      final IdentityRetryService identityRetryService,
      final OperateProperties operateProperties,
      final Identity identity,
      final SecurityContextWrapper securityContextWrapper) {
    this.identityRetryService = identityRetryService;
    this.operateProperties = operateProperties;
    this.identity = identity;
    this.securityContextWrapper = securityContextWrapper;
  }

  public String getRedirectUrl(final HttpServletRequest req) {
    return identity
        .authentication()
        .authorizeUriBuilder(getRedirectURI(req, IDENTITY_CALLBACK_URI))
        .build()
        .toString();
  }

  public void logout() {
    final IdentityAuthentication authentication =
        (IdentityAuthentication) securityContextWrapper.getAuthentication();
    identity.authentication().revokeToken(authentication.getTokens().getRefreshToken());
  }

  public String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    final String fixedRedirectRootUrl = operateProperties.getIdentity().getRedirectRootUrl();

    String redirectRootUri;
    if (StringUtils.isNotBlank(fixedRedirectRootUrl)) {
      redirectRootUri = fixedRedirectRootUrl;
    } else {
      redirectRootUri = req.getScheme() + "://" + req.getServerName();
      if ((req.getScheme().equals("http") && req.getServerPort() != 80)
          || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
        redirectRootUri += ":" + req.getServerPort();
      }
    }

    final String result;
    if (contextPathIsUUID(req.getContextPath())) {
      final String clusterId = req.getContextPath().replace("/", "");
      result = redirectRootUri + redirectTo + "?uuid=" + clusterId;
    } else {
      result = redirectRootUri + req.getContextPath() + redirectTo;
    }
    return result;
  }

  public IdentityAuthentication getAuthenticationFor(
      final HttpServletRequest req, final AuthCodeDto authCodeDto) throws Exception {
    final Tokens tokens =
        identityRetryService.requestWithRetry(
            () ->
                identity
                    .authentication()
                    .exchangeAuthCode(authCodeDto, getRedirectURI(req, IDENTITY_CALLBACK_URI)),
            "IdentityService#getAuthentication");
    final IdentityAuthentication authentication = new IdentityAuthentication();
    authentication.authenticate(tokens);
    return authentication;
  }

  private boolean contextPathIsUUID(final String contextPath) {
    try {
      UUID.fromString(contextPath.replace("/", ""));
      return true;
    } catch (final Exception e) {
      // Assume it isn't a UUID
      return false;
    }
  }
}
