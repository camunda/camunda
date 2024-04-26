/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.api;

import io.camunda.identity.usermanagement.User;
import io.camunda.identity.usermanagement.service.UsersService;
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
public class UsersController {
  private final UsersService usersService;

  public UsersController(final UsersService usersService) {
    this.usersService = usersService;
  }

  @PostMapping
  public User createUser(@RequestBody final User user) {
    return usersService.createUser(user);
  }

  @DeleteMapping("/{username}")
  public void deleteUser(@PathVariable("username") final String userName) {
    usersService.deleteUser(userName);
  }

  @GetMapping("/{username}")
  public User findUserByUsername(@PathVariable("username") final String username) {
    return usersService
        .findUserByUsername(username)
        .orElseThrow(() -> new RuntimeException("not found"));
  }

  @GetMapping
  public List<User> findAllUsers() {
    return usersService.findAllUsers();
  }

  @PutMapping("/{username}/enable")
  public void enableUser(final String username) {
    usersService.enableUser(username);
  }

  @PutMapping("/{username}/disable")
  public void disableUser(final String username) {
    usersService.disableUser(username);
  }
}
