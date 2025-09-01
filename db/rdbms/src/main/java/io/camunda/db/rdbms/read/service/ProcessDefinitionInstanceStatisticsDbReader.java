/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.columns.SearchColumn;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsReader;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class ProcessDefinitionInstanceStatisticsDbReader
    extends AbstractEntityReader<ProcessDefinitionInstanceStatisticsEntity>
    implements ProcessDefinitionInstanceStatisticsReader {

  public ProcessDefinitionInstanceStatisticsDbReader() {
    super(new SearchColumn[] {});
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    // Not implemented yet
    return SearchQueryResult.empty();
  }
}
