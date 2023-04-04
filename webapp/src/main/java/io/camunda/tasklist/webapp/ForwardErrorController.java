/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
