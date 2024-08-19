/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

public class AssigneesClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public AssigneesClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public List<UserDto> getAssigneesByIdsWithoutAuthentication(final List<String> ids) {
    return getRequestExecutor()
        .buildGetAssigneesByIdRequest(ids)
        .executeAndReturnList(UserDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForAssignees(
      final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    return getRequestExecutor()
        .buildSearchForAssigneesRequest(requestDto)
        .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForAssignees(
      final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    return getRequestExecutor()
        .buildSearchForAssigneesRequest(requestDto)
        .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForAssigneesAsUser(
      final String username,
      final String password,
      final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildSearchForAssigneesRequest(requestDto)
        .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
