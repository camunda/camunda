/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;

public interface IncidentSearchClient {

  IncidentEntity getIncident(final long key);

  SearchQueryResult<IncidentEntity> searchIncidents(IncidentQuery filter);

  SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity>
      incidentProcessInstanceStatisticsByError(IncidentProcessInstanceStatisticsByErrorQuery query);

  IncidentSearchClient withSecurityContext(SecurityContext securityContext);

  SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity>
      searchIncidentProcessInstanceStatisticsByDefinition(
          final IncidentProcessInstanceStatisticsByDefinitionQuery query);
}
