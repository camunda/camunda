/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.columns.IncidentProcessInstanceStatisticsByErrorSearchColumn;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByErrorReader;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class IncidentProcessInstanceStatisticsByErrorDbReader
    extends AbstractEntityReader<IncidentProcessInstanceStatisticsByErrorEntity>
    implements IncidentProcessInstanceStatisticsByErrorReader {

  // TODO: implement aggregation logic for rdbms reader - #42652
  public IncidentProcessInstanceStatisticsByErrorDbReader(final IncidentMapper incidentMapper) {
    super(IncidentProcessInstanceStatisticsByErrorSearchColumn.values());
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity> aggregate(
      final IncidentProcessInstanceStatisticsByErrorQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    return SearchQueryResult.empty();
  }
}
