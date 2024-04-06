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
package io.camunda.tasklist.webapp;

import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;

import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ForwardErrorController implements ErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);

  @Autowired private TasklistProfileService profileService;

  @RequestMapping("/error")
  public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
    final String requestedURI =
        (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (requestedURI == null) {
      return forwardToRootPage();
    }
    if (profileService.isLoginDelegated()
        && !requestedURI.contains(LOGIN_RESOURCE)
        && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request, requestedURI);
    } else {
      if (requestedURI.contains("/graphql")) {
        final ModelAndView modelAndView = new ModelAndView();
        final Integer statusCode =
            (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        final Exception exception =
            (Exception)
                request.getAttribute(
                    "org.springframework.boot.web.servlet.error.DefaultErrorAttributes.ERROR");
        modelAndView.addObject("message", profileService.getMessageByProfileFor(exception));
        modelAndView.setStatus(HttpStatus.valueOf(statusCode));
        return modelAndView;
      }
      return forwardToRootPage();
    }
  }

  private ModelAndView forwardToRootPage() {
    final ModelAndView modelAndView = new ModelAndView("forward:/");
    modelAndView.setStatus(HttpStatus.OK);
    return modelAndView;
  }

  private ModelAndView saveRequestAndRedirectToLogin(
      final HttpServletRequest request, final String requestedURI) {
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ", requestedURI, LOGIN_RESOURCE);
    final String queryString = request.getQueryString();
    if (ConversionUtils.stringIsEmpty(queryString)) {
      request.getSession(true).setAttribute(REQUESTED_URL, requestedURI);
    } else {
      request.getSession(true).setAttribute(REQUESTED_URL, requestedURI + "?" + queryString);
    }

    final ModelAndView modelAndView = new ModelAndView("redirect:" + LOGIN_RESOURCE);
    modelAndView.setStatus(HttpStatus.FOUND);
    return modelAndView;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }
}
