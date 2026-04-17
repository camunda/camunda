/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import io.camunda.gateway.protocol.model.IncidentFilter;
import io.camunda.gateway.protocol.model.OffsetPagination;
import io.camunda.gateway.protocol.model.SearchQueryPageRequest;

/**
 * Mapper for converting simple search request models into advanced search query representations.
 *
 * <p>This class provides static helper methods to:
 *
 * <ul>
 *   <li>Validate and map simple {@link SearchQueryPageRequest} instances to advanced pagination
 *       requests (e.g. limit, cursor, and offset-based pagination).
 *   <li>Map simple filters to advanced query components with equality semantics.
 */
public class SimpleSearchQueryMapper {

  /** Identity pass-through. Pagination types no longer form a class hierarchy. */
  public static SearchQueryPageRequest toPageRequest(final SearchQueryPageRequest page) {
    return page;
  }

  /**
   * Identity pass-through. Previously converted simple filter types to advanced filter types; now
   * both types are the same after the code generation refactor.
   */
  public static io.camunda.gateway.protocol.model.IncidentFilter toIncidentFilter(
      final IncidentFilter filter) {
    return filter;
  }

  /**
   * @see #toIncidentFilter — identity pass-through after simple types were removed.
   */
  public static io.camunda.gateway.protocol.model.ProcessInstanceFilter toProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter filter) {
    return filter;
  }

  /**
   * @see #toIncidentFilter — identity pass-through after simple types were removed.
   */
  public static io.camunda.gateway.protocol.model.ProcessDefinitionFilter toProcessDefinitionFilter(
      final io.camunda.gateway.protocol.model.ProcessDefinitionFilter filter) {
    return filter;
  }

  /**
   * @see #toIncidentFilter — identity pass-through after simple types were removed.
   */
  public static io.camunda.gateway.protocol.model.VariableFilter toVariableFilter(
      final io.camunda.gateway.protocol.model.VariableFilter filter) {
    return filter;
  }

  /**
   * @see #toIncidentFilter — identity pass-through after simple types were removed.
   */
  public static io.camunda.gateway.protocol.model.UserTaskFilter toUserTaskFilter(
      final io.camunda.gateway.protocol.model.UserTaskFilter filter) {
    return filter;
  }

  /**
   * @see #toIncidentFilter — identity pass-through after simple types were removed.
   */
  public static io.camunda.gateway.protocol.model.UserTaskVariableFilter toUserTaskVariableFilter(
      final io.camunda.gateway.protocol.model.UserTaskVariableFilter simple) {
    return simple;
  }

  /**
   * @see #toIncidentFilter — identity pass-through after simple types were removed.
   */
  public static OffsetPagination toOffsetPagination(
      final io.camunda.gateway.protocol.model.OffsetPagination simple) {
    return simple;
  }
}
