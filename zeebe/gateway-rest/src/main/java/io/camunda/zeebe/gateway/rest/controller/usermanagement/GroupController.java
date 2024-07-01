/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.service.GroupService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupDto;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchResponseDto;
import io.camunda.zeebe.gateway.protocol.rest.SearchRequestDto;
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
@RequestMapping("/v2/groups")
public class GroupController {
  private final GroupService groupService;

  public GroupController(final GroupService groupService) {
    this.groupService = groupService;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CamundaGroupDto createGroup(@RequestBody final CamundaGroupDto groupDto) {
    return mapToGroupDto(groupService.createGroup(mapToGroup(groupDto)));
  }

  @DeleteMapping(path = "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteGroup(@PathVariable(name = "id") final Long groupId) {
    groupService.deleteGroupById(groupId);
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CamundaGroupDto findGroupById(@PathVariable(name = "id") final Long groupId) {
    return mapToGroupDto(groupService.findGroupById(groupId));
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public GroupSearchResponseDto findAllGroups(
      @RequestBody final SearchRequestDto searchRequestDto) {
    final GroupSearchResponseDto responseDto = new GroupSearchResponseDto();
    final List<CamundaGroupDto> allGroupDtos =
        groupService.findAllGroups().stream().map(this::mapToGroupDto).toList();
    responseDto.setItems(allGroupDtos);

    return responseDto;
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CamundaGroupDto updateGroup(
      @PathVariable(name = "id") final Long groupId, @RequestBody final CamundaGroupDto groupDto) {
    return mapToGroupDto(groupService.updateGroup(groupId, mapToGroup(groupDto)));
  }

  private CamundaGroup mapToGroup(final CamundaGroupDto groupDto) {
    return new CamundaGroup(groupDto.getId(), groupDto.getName());
  }

  private CamundaGroupDto mapToGroupDto(final CamundaGroup group) {
    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setId(group.id());
    camundaGroupDto.setName(group.name());
    return camundaGroupDto;
  }
}
