/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserDto;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordDto;
import io.camunda.zeebe.gateway.protocol.rest.SearchRequestDto;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponseDto;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@ZeebeRestController
@RequestMapping("/v2/users")
public class UserController {
  private final UserService userService;

  public UserController(final UserService userService) {
    this.userService = userService;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CamundaUserDto createUser(
      @RequestBody final CamundaUserWithPasswordDto userWithPasswordDto) {
    return mapToCamundaUserDto(userService.createUser(mapToUserWithPassword(userWithPasswordDto)));
  }

  @DeleteMapping(path = "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable final Long id) {
    userService.deleteUser(id);
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CamundaUserDto findUserByUsername(@PathVariable final Long id) {
    return mapToCamundaUserDto(userService.findUserById(id));
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public UserSearchResponseDto findAllUsers(@RequestBody final SearchRequestDto searchRequestDto) {
    final UserSearchResponseDto responseDto = new UserSearchResponseDto();
    final List<CamundaUserDto> allUsers =
        userService.findAllUsers().stream().map(this::mapToCamundaUserDto).toList();
    responseDto.setItems(allUsers);

    return responseDto;
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CamundaUserDto updateUser(
      @PathVariable final Long id, @RequestBody final CamundaUserWithPasswordDto user) {
    return mapToCamundaUserDto(userService.updateUser(id, mapToUserWithPassword(user)));
  }

  private CamundaUserWithPassword mapToUserWithPassword(final CamundaUserWithPasswordDto dto) {
    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();

    camundaUserWithPassword.setId(dto.getId());
    camundaUserWithPassword.setUsername(dto.getUsername());
    camundaUserWithPassword.setPassword(dto.getPassword());
    camundaUserWithPassword.setEmail(dto.getEmail());
    camundaUserWithPassword.setEnabled(dto.getEnabled());

    return camundaUserWithPassword;
  }

  private CamundaUserDto mapToCamundaUserDto(final CamundaUser camundaUser) {
    final CamundaUserDto camundaUserDto = new CamundaUserDto();
    camundaUserDto.setId(camundaUser.getId());
    camundaUserDto.setUsername(camundaUser.getUsername());
    camundaUserDto.setEmail(camundaUser.getEmail());
    camundaUserDto.setEnabled(camundaUser.isEnabled());

    return camundaUserDto;
  }
}
