/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.OperateProfileService.SSO_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.REQUESTED_URL;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_CALLBACK_URI;

import com.auth0.IdentityVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile(SSO_AUTH_PROFILE)
public class SSOController {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();
  @Autowired private Auth0Service auth0Service;
  private SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  @RequestMapping(
      value = LOGIN_RESOURCE,
      method = {RequestMethod.GET, RequestMethod.POST})
  public String login(final HttpServletRequest req, final HttpServletResponse res) {
    final String authorizeUrl = auth0Service.getAuthorizeUrl(req, res);
    logger.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  @GetMapping(value = SSO_CALLBACK_URI)
  public void loggedInCallback(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    logger.debug(
        "Called back by auth0 with {} {} and SessionId: {}",
        req.getRequestURI(),
        req.getQueryString(),
        req.getSession().getId());
    try {
      final var authentication = auth0Service.authenticate(req, res);

      final var context = securityContextHolderStrategy.createEmptyContext();
      context.setAuthentication(authentication);
      securityContextHolderStrategy.setContext(context);
      securityContextRepository.saveContext(context, req, res);

      sessionExpiresWhenAuthenticationExpires(req);

      redirectToPage(req, res);
    } catch (InsufficientAuthenticationException iae) {
      // remove logout, user might just not be allowed to access. Redirect to no permission
      clearContextAndRedirectToNoPermission(req, res, iae);
    } catch (Auth0ServiceException ase) {
      handleAuth0Exception(ase, req, res);
    }
  }

  private void handleAuth0Exception(
      final Auth0ServiceException ase, final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    logger.error("Error in authentication callback: ", ase);
    Throwable cause = ase.getCause();
    if (cause != null) {
      if (cause instanceof InsufficientAuthenticationException) {
        logoutAndRedirectToNoPermissionPage(req, res);
      } else if (cause instanceof IdentityVerificationException
          || cause instanceof AuthenticationException) {
        clearContextAndRedirectToNoPermission(req, res, cause);
      } else {
        logout(req, res);
      }
    } else {
      logout(req, res);
    }
  }

  private void redirectToPage(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    Object originalRequestUrl = req.getSession().getAttribute(REQUESTED_URL);
    if (originalRequestUrl != null) {
      res.sendRedirect(req.getContextPath() + originalRequestUrl);
    } else {
      res.sendRedirect(req.getContextPath() + ROOT);
    }
  }

  /**
   * Is called when there was an in authentication or authorization
   *
   * @return Message as String
   */
  @RequestMapping(value = NO_PERMISSION)
  @ResponseBody
  public String noPermissions() {
    return "No permission for Operate - Please check your operate configuration or cloud configuration.";
  }

  /**
   * Logout - Invalidates session and logout from auth0, after that redirects to root url.
   *
   * @param req a HttpServletRequest
   * @param res a HttpServletResponse
   * @throws IOException when communication with auth0 fails
   */
  @RequestMapping(value = LOGOUT_RESOURCE)
  public void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.debug("logout user");
    cleanup(req);
    logoutFromAuth0(res, auth0Service.getRedirectURI(req, ROOT));
  }

  protected void clearContextAndRedirectToNoPermission(
      HttpServletRequest req, HttpServletResponse res, Throwable t) throws IOException {
    logger.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(auth0Service.getRedirectURI(req, NO_PERMISSION));
  }

  protected void logoutAndRedirectToNoPermissionPage(
      HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.warn("User is authenticated but there are no permissions.");
    cleanup(req);
    logoutFromAuth0(res, auth0Service.getRedirectURI(req, NO_PERMISSION));
  }

  protected void cleanup(HttpServletRequest req) {
    req.getSession().invalidate();

    final var context = securityContextHolderStrategy.getContext();
    if (context != null) {
      context.setAuthentication(null);
      securityContextHolderStrategy.clearContext();
    }
  }

  protected void logoutFromAuth0(HttpServletResponse res, String returnTo) throws IOException {
    res.sendRedirect(auth0Service.getLogoutUrlFor(returnTo));
  }

  private void sessionExpiresWhenAuthenticationExpires(final HttpServletRequest req) {
    req.getSession().setMaxInactiveInterval(-1);
  }
}
