/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/post-logout")
public class PostLogoutController {

  @GetMapping()
  public String postLogout(final HttpServletRequest request) {
    final HttpSession session = request.getSession(false);
    String redirect = null;

    if (session != null) {
      redirect = (String) session.getAttribute("postLogoutRedirect");
      session.removeAttribute("postLogoutRedirect"); // clean up
    }

    if (redirect == null) {
      redirect = "/";
    }

    return "redirect:" + redirect;
  }
}
