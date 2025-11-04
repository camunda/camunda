/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.columns.SearchColumn;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceVersionStatisticsReader;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class ProcessDefinitionInstanceVersionStatisticsDbReader
    extends AbstractEntityReader<ProcessDefinitionInstanceVersionStatisticsEntity>
    implements ProcessDefinitionInstanceVersionStatisticsReader {

  public ProcessDefinitionInstanceVersionStatisticsDbReader() {
    super(new SearchColumn[] {});
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceVersionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    // Not implemented yet
    return SearchQueryResult.empty();
  }
}
