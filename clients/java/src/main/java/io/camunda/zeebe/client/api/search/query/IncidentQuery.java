/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.search.query;

import io.camunda.zeebe.client.api.search.filter.IncidentFilter;
import io.camunda.zeebe.client.api.search.response.Incident;
import io.camunda.zeebe.client.api.search.sort.IncidentSort;

public interface IncidentQuery
    extends TypedSearchQueryRequest<IncidentFilter, IncidentSort, IncidentQuery>,
        FinalSearchQueryStep<Incident> {}
