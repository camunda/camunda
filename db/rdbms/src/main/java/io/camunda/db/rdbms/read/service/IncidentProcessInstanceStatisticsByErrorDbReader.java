/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.IncidentProcessInstanceStatisticsByErrorDbQuery;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.columns.IncidentProcessInstanceStatisticsByErrorSearchColumn;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByErrorReader;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentProcessInstanceStatisticsByErrorDbReader
    extends AbstractEntityReader<IncidentProcessInstanceStatisticsByErrorEntity>
    implements IncidentProcessInstanceStatisticsByErrorReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(IncidentProcessInstanceStatisticsByErrorDbReader.class);

  private final IncidentMapper incidentMapper;

  public IncidentProcessInstanceStatisticsByErrorDbReader(
      final IncidentMapper incidentMapper, final RdbmsReaderConfig readerConfig) {
    super(IncidentProcessInstanceStatisticsByErrorSearchColumn.values(), readerConfig);
    this.incidentMapper = incidentMapper;
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity> aggregate(
      final IncidentProcessInstanceStatisticsByErrorQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var dbSort =
        convertSort(
            query.sort(),
            IncidentProcessInstanceStatisticsByErrorSearchColumn.ACTIVE_INSTANCES_WITH_ERROR_COUNT);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());

    final var dbQuery =
        IncidentProcessInstanceStatisticsByErrorDbQuery.of(
            builder ->
                builder
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace(
        "[RDBMS DB] Aggregate for incident process instance statistics by error with query {}",
        dbQuery);

    return executePagedQuery(
        () -> incidentMapper.processInstanceStatisticsByErrorCount(dbQuery),
        () -> incidentMapper.processInstanceStatisticsByError(dbQuery),
        dbQuery.page(),
        dbSort);
  }
}
