/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.columns.IncidentProcessInstanceStatisticsByDefinitionSearchColumn;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByDefinitionReader;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class IncidentProcessInstanceStatisticsByDefinitionDbReader
    extends AbstractEntityReader<IncidentProcessInstanceStatisticsByDefinitionEntity>
    implements IncidentProcessInstanceStatisticsByDefinitionReader {

  public IncidentProcessInstanceStatisticsByDefinitionDbReader(
      final IncidentMapper incidentMapper) {
    super(IncidentProcessInstanceStatisticsByDefinitionSearchColumn.values());
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> aggregate(
      final IncidentProcessInstanceStatisticsByDefinitionQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    return SearchQueryResult.empty();
  }
}
