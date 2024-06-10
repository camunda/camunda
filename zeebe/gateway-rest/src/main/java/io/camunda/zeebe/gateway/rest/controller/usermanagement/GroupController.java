/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.service.GroupService;
import io.camunda.identity.usermanagement.service.MembershipService;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.dto.AssignUserToGroupRequest;
import io.camunda.zeebe.gateway.rest.dto.search.SearchRequestDto;
import io.camunda.zeebe.gateway.rest.dto.search.SearchResponseDto;
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
@RequestMapping("/v2")
public class GroupController {
  private final GroupService groupService;
  private final MembershipService membershipService;

  public GroupController(
      final GroupService groupService, final MembershipService membershipService) {
    this.groupService = groupService;
    this.membershipService = membershipService;
  }

  @PostMapping(
      path = "/groups",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CamundaGroup createGroup(@RequestBody final CamundaGroup group) {
    return groupService.createGroup(group);
  }

  @DeleteMapping(path = "/groups/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteGroup(@PathVariable(name = "id") final Long groupId) {
    groupService.deleteGroupById(groupId);
  }

  @GetMapping(
      path = "/groups/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CamundaGroup findGroupById(@PathVariable(name = "id") final Long groupId) {
    return groupService.findGroupById(groupId);
  }

  @PostMapping(
      path = "/groups/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponseDto<CamundaGroup> findAllGroups(
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<CamundaGroup> responseDto = new SearchResponseDto<>();
    final List<CamundaGroup> allGroups = groupService.findAllGroups();
    responseDto.setItems(allGroups);

    return responseDto;
  }

  @PutMapping(
      path = "/groups/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CamundaGroup updateGroup(
      @PathVariable(name = "id") final Long groupId, @RequestBody final CamundaGroup group) {
    return groupService.renameGroupById(groupId, group);
  }

  @PostMapping(path = "/groups/{id}/users", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignUserToGroup(
      @PathVariable(name = "id") final Long groupId,
      @RequestBody final AssignUserToGroupRequest assignRequest) {
    membershipService.addUserToGroupByIds(assignRequest.userId(), groupId);
  }

  @DeleteMapping(path = "/groups/{id}/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeUserFromGroup(
      @PathVariable(name = "id") final Long groupId, @PathVariable final Long userId) {
    membershipService.removeUserFromGroupByIds(userId, groupId);
  }

  @PostMapping(
      path = "/groups/{id}/users/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponseDto<CamundaUser> findAllUsersOfGroup(
      @PathVariable(name = "id") final Long groupId,
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<CamundaUser> responseDto = new SearchResponseDto<>();
    final List<CamundaUser> allUsers = membershipService.getUsersOfGroupById(groupId);
    responseDto.setItems(allUsers);

    return responseDto;
  }
}
