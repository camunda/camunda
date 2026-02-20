/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionInstanceStatisticsDbQuery;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.columns.ProcessDefinitionInstanceStatisticsSearchColumn;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsReader;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionInstanceStatisticsDbReader
    extends AbstractEntityReader<ProcessDefinitionInstanceStatisticsEntity>
    implements ProcessDefinitionInstanceStatisticsReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionInstanceStatisticsDbReader.class);

  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionInstanceStatisticsDbReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    super(ProcessDefinitionInstanceStatisticsSearchColumn.values(), readerConfig);
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceStatisticsQuery query) {
    return aggregate(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var dbSort =
        convertSort(
            query.sort(), ProcessDefinitionInstanceStatisticsSearchColumn.PROCESS_DEFINITION_ID);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());

    final var dbQuery =
        ProcessDefinitionInstanceStatisticsDbQuery.of(
            builder ->
                builder
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace(
        "[RDBMS DB] Search for process definition instance statistics with query {}", dbQuery);

    return executePagedQuery(
        () -> processDefinitionMapper.processInstanceStatisticsCount(dbQuery),
        () -> processDefinitionMapper.processInstanceStatistics(dbQuery),
        dbQuery.page(),
        dbSort);
  }
}
