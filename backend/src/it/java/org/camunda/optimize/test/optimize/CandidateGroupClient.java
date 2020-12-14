/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupSearchRequestDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

@AllArgsConstructor
public class CandidateGroupClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<GroupDto> getCandidateGroupsByIdsWithoutAuthentication(final List<String> ids) {
    return getRequestExecutor()
      .buildGetCandidateGroupsByIdRequest(ids)
      .withoutAuthentication()
      .executeAndReturnList(GroupDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForCandidateGroups(final AssigneeCandidateGroupSearchRequestDto requestDto) {
    return getRequestExecutor()
      .buildSearchForCandidateGroupsRequest(requestDto)
      .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
