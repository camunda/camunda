/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.controller;

import static io.camunda.authentication.utils.RequestValidationUtils.isAllowedRedirect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/post-logout")
public class PostLogoutController {

  public static final String POST_LOGOUT_REDIRECT_ATTRIBUTE = "postLogoutRedirect";
  private static final Logger LOG = LoggerFactory.getLogger(PostLogoutController.class);
  private static final String DEFAULT_REDIRECT_PATH = "/";

  @GetMapping()
  public String postLogout(final HttpServletRequest request) {
    final HttpSession session = request.getSession(false);
    String redirect = null;

    if (session != null) {
      final Object postLogoutRedirect = session.getAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE);
      // clean up
      session.removeAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE);

      if (postLogoutRedirect instanceof String) {
        redirect = (String) postLogoutRedirect;
      }
    }

    if (!isAllowedRedirect(request, redirect)) {
      LOG.trace(
          """
          No valid post-logout redirect URL found in session, falling back to default: '{}'
          """,
          DEFAULT_REDIRECT_PATH);
      redirect = DEFAULT_REDIRECT_PATH;
    }
    return "redirect:" + redirect;
  }
}
