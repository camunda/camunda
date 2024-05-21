/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.controller;

import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;

  public UserController(final UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  public CamundaUser createUser(@RequestBody final CamundaUserWithPassword user) {
    return userService.createUser(user);
  }

  @DeleteMapping("/{username}")
  public void deleteUser(@PathVariable("username") final String username) {
    userService.deleteUser(username);
  }

  @GetMapping("/{username}")
  public CamundaUser findUserByUsername(@PathVariable("username") final String username) {
    return userService.findUserByUsername(username);
  }

  @GetMapping
  public List<CamundaUser> findAllUsers() {
    return userService.findAllUsers();
  }

  @PutMapping("/{username}")
  public CamundaUser updateUser(
      @PathVariable("username") final String username, final CamundaUserWithPassword user) {
    return userService.updateUser(username, user);
  }
}
