/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.identity;

import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import io.camunda.zeebe.gateway.rest.identity.search.SearchRequestDto;
import io.camunda.zeebe.gateway.rest.identity.search.SearchResponseDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2")
public class UserControllerURA {
  private final UserService userService;

  public UserControllerURA(final UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CamundaUser createUser(@RequestBody final CamundaUserWithPassword user) {
    return userService.createUser(user);
  }

  @DeleteMapping("/users/{username}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable("username") final String username) {
    userService.deleteUser(username);
  }

  @GetMapping("/users/{username}")
  public CamundaUser findUserByUsername(@PathVariable("username") final String username) {
    return userService.findUserByUsername(username);
  }

  @PostMapping("/users/search")
  public SearchResponseDto<CamundaUser> findAllUsers(
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<CamundaUser> responseDto = new SearchResponseDto<>();
    final List<CamundaUser> allUsers = userService.findAllUsers();
    responseDto.setItems(allUsers);

    return responseDto;
  }

  @PutMapping("/users/{username}")
  public CamundaUser updateUser(
      @PathVariable("username") final String username, final CamundaUserWithPassword user) {
    return userService.updateUser(username, user);
  }
}
