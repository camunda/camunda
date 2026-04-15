/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupCreateRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupUpdateRequestContract;
import io.camunda.gateway.mapping.http.validator.GroupRequestValidator;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class GroupMapper {

  private final GroupRequestValidator groupRequestValidator;

  public GroupMapper(final GroupRequestValidator groupRequestValidator) {
    this.groupRequestValidator = groupRequestValidator;
  }

  public Either<ProblemDetail, GroupMemberDTO> toGroupMemberRequest(
      final String groupId, final String memberId, final EntityType entityType) {
    return RequestMapper.getResult(
        groupRequestValidator.validateMemberRequest(groupId, memberId, entityType),
        () -> new GroupMemberDTO(groupId, memberId, entityType));
  }

  public Either<ProblemDetail, GroupDTO> toGroupCreateRequest(
      final GroupCreateRequestContract request) {
    return RequestMapper.getResult(
        groupRequestValidator.validateCreateRequest(request),
        () -> new GroupDTO(request.groupId(), request.name(), request.description()));
  }

  public Either<ProblemDetail, GroupDTO> toGroupUpdateRequest(
      final GroupUpdateRequestContract request, final String groupId) {
    return RequestMapper.getResult(
        groupRequestValidator.validateUpdateRequest(groupId, request),
        () -> new GroupDTO(groupId, request.name(), request.description()));
  }
}
