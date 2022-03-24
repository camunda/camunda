/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp;

import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.REQUESTED_URL;

import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.security.OperateProfileService;
import javax.servlet.RequestDispatcher;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class ForwardErrorController implements ErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);
  @Autowired
  private OperateProfileService operateProfileService;

  @RequestMapping(value = "/error")
  public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
    final String requestedURI = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (requestedURI == null) {
      return forwardToRootPage();
    }
    if (operateProfileService.isLoginDelegated()  && !requestedURI.contains(LOGIN_RESOURCE) && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request, requestedURI);
    } else {
      if (requestedURI.contains("/api/")) {
        ModelAndView modelAndView = new ModelAndView();
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Exception exception = (Exception) request.getAttribute(
            "org.springframework.boot.web.servlet.error.DefaultErrorAttributes.ERROR");
        modelAndView.addObject("message", operateProfileService.getMessageByProfileFor(exception));
        modelAndView.setStatus(HttpStatus.valueOf(statusCode));
        return modelAndView;
      }
      return forwardToRootPage();
    }
  }

  private ModelAndView forwardToRootPage() {
    final ModelAndView modelAndView = new ModelAndView("forward:/");
    // Is it really necessary to set status to OK (for frontend)?
    modelAndView.setStatus(HttpStatus.OK);
    return modelAndView;
  }
  private ModelAndView saveRequestAndRedirectToLogin(
      final HttpServletRequest request, final String requestedURI) {
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ",
        requestedURI,
        LOGIN_RESOURCE);
    String queryString = request.getQueryString();
    if(ConversionUtils.stringIsEmpty(queryString)){
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
