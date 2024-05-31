/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.identity.api;

import io.camunda.identity.user.Group;
import io.camunda.identity.usermanagement.service.GroupService;
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

  public GroupControllerURA(final GroupService groupService) {
    this.groupService = groupService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Group createGroup(@RequestBody final Group group) {
    return groupService.createGroup(group);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteGroup(@PathVariable final Integer id) {
    groupService.deleteGroupById(id);
  }

  @GetMapping("/{id}")
  public Group findGroupById(@PathVariable final Integer id) {
    return groupService.findGroupById(id);
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
  public Group updateGroup(@PathVariable final Integer id, @RequestBody final Group group) {
    return groupService.renameGroupById(id, group);
  }
}
