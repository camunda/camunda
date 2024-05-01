/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.controller;

import io.camunda.identity.security.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class IndexController {
  private final CurrentUserService currentUserService;

  public IndexController(CurrentUserService currentUserService) {
    this.currentUserService = currentUserService;
  }

  @GetMapping("/")
  @ResponseStatus(HttpStatus.OK)
  public String index(final Model model) {
    model.addAttribute("username", currentUserService.getCurrentUserDetails().username());
    return "index";
  }
}
