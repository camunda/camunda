/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionInstanceVersionStatisticsDbQuery;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.columns.ProcessDefinitionInstanceVersionStatisticsSearchColumn;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceVersionStatisticsReader;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionInstanceVersionStatisticsDbReader
    extends AbstractEntityReader<ProcessDefinitionInstanceVersionStatisticsEntity>
    implements ProcessDefinitionInstanceVersionStatisticsReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionInstanceVersionStatisticsDbReader.class);

  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionInstanceVersionStatisticsDbReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    super(ProcessDefinitionInstanceVersionStatisticsSearchColumn.values(), readerConfig);
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceVersionStatisticsQuery query) {
    return aggregate(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceVersionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var dbSort =
        convertSort(
            query.sort(),
            ProcessDefinitionInstanceVersionStatisticsSearchColumn.PROCESS_DEFINITION_VERSION);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());

    final var dbQuery =
        ProcessDefinitionInstanceVersionStatisticsDbQuery.of(
            builder ->
                builder
                    .filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace(
        "[RDBMS DB] Search for process definition instance version statistics with query {}",
        dbQuery);

    return executePagedQuery(
        () -> processDefinitionMapper.processInstanceVersionStatisticsCount(dbQuery),
        () -> processDefinitionMapper.processInstanceVersionStatistics(dbQuery),
        dbQuery.page(),
        dbSort);
  }
}
