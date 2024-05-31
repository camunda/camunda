/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.identity.api.user;

import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.Group;
import io.camunda.identity.usermanagement.service.GroupService;
import io.camunda.identity.usermanagement.service.MembershipService;
import io.camunda.zeebe.gateway.rest.identity.api.search.SearchRequestDto;
import io.camunda.zeebe.gateway.rest.identity.api.search.SearchResponseDto;
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
@RequestMapping("/v2/groups")
public class GroupControllerURA {
  private final GroupService groupService;
  private final MembershipService membershipService;

  public GroupControllerURA(
      final GroupService groupService, final MembershipService membershipService) {
    this.groupService = groupService;
    this.membershipService = membershipService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Group createGroup(@RequestBody final Group group) {
    return groupService.createGroup(group);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteGroup(@PathVariable(name = "id") final Integer groupId) {
    groupService.deleteGroupById(groupId);
  }

  @GetMapping("/{id}")
  public Group findGroupById(@PathVariable(name = "id") final Integer groupId) {
    return groupService.findGroupById(groupId);
  }

  @PostMapping("/search")
  public SearchResponseDto<Group> findAllGroups(
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<Group> responseDto = new SearchResponseDto<>();
    final List<Group> allGroups = groupService.findAllGroups();
    responseDto.setItems(allGroups);

    return responseDto;
  }

  @PutMapping("/{id}")
  public Group updateGroup(
      @PathVariable(name = "id") final Integer groupId, @RequestBody final Group group) {
    return groupService.renameGroupById(groupId, group);
  }

  @PostMapping("/{id}/users")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignUserToGroup(
      @PathVariable(name = "id") final Integer groupId,
      @RequestBody final AssignUserToGroupRequest assignRequest) {
    membershipService.addUserToGroup(assignRequest.userId(), groupId);
  }

  @DeleteMapping("/{id}/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeUserFromGroup(
      @PathVariable(name = "id") final Integer groupId, @PathVariable final Integer userId) {
    membershipService.removeUserFromGroup(userId, groupId);
  }

  @PostMapping("/{id}/users/search")
  public SearchResponseDto<CamundaUser> findAllUsersOfGroup(
      @PathVariable(name = "id") final Integer groupId,
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<CamundaUser> responseDto = new SearchResponseDto<>();
    final List<CamundaUser> allUsers = membershipService.getUsersOfGroup(groupId);
    responseDto.setItems(allUsers);

    return responseDto;
  }
}
