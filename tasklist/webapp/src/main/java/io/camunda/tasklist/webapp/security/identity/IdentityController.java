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

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IDENTITY_CALLBACK_URI;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityController {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired private IdentityService identityService;

  private SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  /**
   * Initiates user login - the user will be redirected to Camunda Account
   *
   * @param req request
   * @return a redirect command to Camunda Account authorize url
   */
  @RequestMapping(
      value = LOGIN_RESOURCE,
      method = {RequestMethod.GET, RequestMethod.POST})
  public String login(final HttpServletRequest req) {
    final String authorizeUrl = identityService.getRedirectUrl(req);
    logger.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  /**
   * Logged in callback - Is called by Camunda Account with results of user authentication (GET)
   * <br>
   * Redirects to root url if successful, otherwise it will redirected to an error url.
   *
   * @param req request
   * @param res response
   * @param authCodeDto authorize response
   * @throws IOException IO Exception
   */
  @GetMapping(value = IDENTITY_CALLBACK_URI)
  public void loggedInCallback(
      final HttpServletRequest req,
      final HttpServletResponse res,
      @RequestParam(required = false, name = "code") String code,
      @RequestParam(required = false, name = "state") String state,
      @RequestParam(required = false, name = "error") String error)
      throws IOException {
    final AuthCodeDto authCodeDto = new AuthCodeDto(code, state, error);
    logger.debug(
        "Called back by identity with {} {}, SessionId: {} and AuthCode {}",
        req.getRequestURI(),
        req.getQueryString(),
        req.getSession().getId(),
        authCodeDto.getCode());
    try {
      final IdentityAuthentication authentication =
          identityService.getAuthenticationFor(req, authCodeDto);

      final var context = securityContextHolderStrategy.createEmptyContext();
      context.setAuthentication(authentication);
      securityContextHolderStrategy.setContext(context);
      securityContextRepository.saveContext(context, req, res);

      redirectToPage(req, res);
    } catch (Exception iae) {
      clearContextAndRedirectToNoPermission(req, res, iae);
    }
  }

  private void redirectToPage(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    final Object originalRequestUrl = req.getSession().getAttribute(REQUESTED_URL);
    if (originalRequestUrl != null) {
      res.sendRedirect(originalRequestUrl.toString());
    } else {
      res.sendRedirect(ROOT);
    }
  }

  /**
   * Is called when there was an in authentication or authorization
   *
   * @return no permissions error message
   */
  @RequestMapping(value = NO_PERMISSION)
  @ResponseBody
  public String noPermissions() {
    return "No permission for Tasklist - Please check your configuration.";
  }

  /**
   * Logout - Invalidates session and logout from auth0, after that redirects to root url.
   *
   * @param req request
   * @param res response
   * @throws IOException exception
   */
  @RequestMapping(value = LOGOUT_RESOURCE)
  public void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.debug("logout user");
    try {
      identityService.logout();
    } catch (Exception e) {
      logger.error("An error occurred in logout process", e);
    }
    cleanup(req);
  }

  protected void clearContextAndRedirectToNoPermission(
      HttpServletRequest req, HttpServletResponse res, Throwable t) throws IOException {
    logger.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(NO_PERMISSION);
  }

  protected void cleanup(HttpServletRequest req) {
    req.getSession().invalidate();

    final var context = securityContextHolderStrategy.getContext();
    if (context != null) {
      context.setAuthentication(null);
      securityContextHolderStrategy.clearContext();
    }
  }
}
