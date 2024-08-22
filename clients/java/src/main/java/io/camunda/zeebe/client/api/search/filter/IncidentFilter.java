/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.search.filter;

import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;

public interface IncidentFilter extends SearchRequestFilter {

  IncidentFilter key(final Long value);

  IncidentFilter processDefinitionKey(final Long value);

  IncidentFilter processInstanceKey(final Long value);

  IncidentFilter type(final String type);

  IncidentFilter flowNodeId(final String value);

  IncidentFilter flowNodeInstanceId(final String value);

  IncidentFilter creationTime(final String value);

  IncidentFilter state(final String value);

  IncidentFilter jobKey(final Long value);

  IncidentFilter tenantId(final String value);

  IncidentFilter hasActiveOperation(final Boolean value);
}
