/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.entities.CamundaUserEntity;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationResponse;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ProblemDetail;

public final class SearchQueryResponseMapper {

  public SearchQueryResponseMapper() {}

  public static Either<ProblemDetail, UserSearchResponse> toUserSearchQueryResponse(
      final SearchQueryResult<CamundaUserEntity> result) {
    final var response = new UserSearchResponse();
    final var total = result.total();
    final var sortValues = result.sortValues();
    final var items = result.items();

    final var page = new SearchQueryPageResponse();
    page.setTotalItems(total);
    response.setPage(page);

    if (sortValues != null) {
      page.setLastSortValues(Arrays.asList(sortValues));
    }

    if (items != null) {
      response.setItems(toUsers(items).get());
    }

    return Either.right(response);
  }

  public static Either<ProblemDetail, AuthorizationSearchResponse>
      toAuthorizationSearchQueryResponse(final SearchQueryResult<AuthorizationEntity> result) {
    final var response = new AuthorizationSearchResponse();
    final var total = result.total();
    final var sortValues = result.sortValues();
    final var items = result.items();

    final var page = new SearchQueryPageResponse();
    page.setTotalItems(total);
    response.setPage(page);

    if (sortValues != null) {
      page.setLastSortValues(Arrays.asList(sortValues));
    }

    if (items != null) {
      response.setItems(toAuthorizations(items).get());
    }

    return Either.right(response);
  }

  public static Either<ProblemDetail, ProcessInstanceSearchQueryResponse>
      toProcessInstanceSearchQueryResponse(final SearchQueryResult<ProcessInstanceEntity> result) {
    final var response = new ProcessInstanceSearchQueryResponse();
    final var total = result.total();
    final var sortValues = result.sortValues();
    final var items = result.items();

    final var page = new SearchQueryPageResponse();
    page.setTotalItems(total);
    response.setPage(page);

    if (sortValues != null) {
      page.setLastSortValues(Arrays.asList(sortValues));
    }

    if (items != null) {
      response.setItems(toProcessInstances(items).get());
    }

    return Either.right(response);
  }

  public static Either<ProblemDetail, List<ProcessInstanceItem>> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return Either.right(
        instances.stream()
            .map(SearchQueryResponseMapper::toProcessInstance)
            .map(Either::get)
            .toList());
  }

  public static Either<ProblemDetail, ProcessInstanceItem> toProcessInstance(
      final ProcessInstanceEntity p) {
    return Either.right(
        new ProcessInstanceItem()
            .tenantId(p.tenantId())
            .key(p.key())
            .processVersion(p.processVersion())
            .bpmnProcessId(p.bpmnProcessId())
            .parentKey(p.parentKey())
            .parentFlowNodeInstanceKey(p.parentFlowNodeInstanceKey())
            .startDate(p.startDate())
            .endDate(p.endDate()));
  }

  public static Either<ProblemDetail, AuthorizationResponse> toAuthorization(
      final AuthorizationEntity authorization) {
    return Either.right(
        new AuthorizationResponse()
            .authorizationKey(authorization.value().authorizationKey())
            .username(authorization.value().username())
            .resourceType(authorization.value().resourceType())
            .resourceKey(authorization.value().resourceKey())
            .permissions(authorization.value().permissions()));
  }

  public static Either<ProblemDetail, List<AuthorizationResponse>> toAuthorizations(
      final List<AuthorizationEntity> authorizations) {
    return Either.right(
        authorizations.stream()
            .map(SearchQueryResponseMapper::toAuthorization)
            .map(Either::get)
            .toList());
  }

  public static Either<ProblemDetail, List<CamundaUserResponse>> toUsers(
      final List<CamundaUserEntity> users) {
    return Either.right(
        users.stream().map(SearchQueryResponseMapper::toUser).map(Either::get).toList());
  }

  public static Either<ProblemDetail, CamundaUserResponse> toUser(final CamundaUserEntity user) {
    return Either.right(
        new CamundaUserResponse()
            .username(user.value().username())
            .email(user.value().email())
            .name(user.value().name()));
  }
}
