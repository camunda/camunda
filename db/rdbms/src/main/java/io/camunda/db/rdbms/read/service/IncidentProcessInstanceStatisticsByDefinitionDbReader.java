/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.IncidentProcessInstanceStatisticsByDefinitionDbQuery;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.columns.IncidentProcessInstanceStatisticsByDefinitionSearchColumn;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByDefinitionReader;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentProcessInstanceStatisticsByDefinitionDbReader
    extends AbstractEntityReader<IncidentProcessInstanceStatisticsByDefinitionEntity>
    implements IncidentProcessInstanceStatisticsByDefinitionReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(IncidentProcessInstanceStatisticsByDefinitionDbReader.class);

  private final IncidentMapper incidentMapper;

  public IncidentProcessInstanceStatisticsByDefinitionDbReader(
      final IncidentMapper incidentMapper, final RdbmsReaderConfig readerConfig) {
    super(IncidentProcessInstanceStatisticsByDefinitionSearchColumn.values(), readerConfig);
    this.incidentMapper = incidentMapper;
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> aggregate(
      final IncidentProcessInstanceStatisticsByDefinitionQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var dbSort =
        convertSort(
            query.sort(),
            IncidentProcessInstanceStatisticsByDefinitionSearchColumn.PROCESS_DEFINITION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final Integer errorMessageHash = query.filter().errorHashCode();
    if (errorMessageHash == null) {
      throw new IllegalArgumentException("Missing required filter: errorHashCode");
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());

    final var dbQuery =
        IncidentProcessInstanceStatisticsByDefinitionDbQuery.of(
            builder ->
                builder
                    .errorMessageHash(errorMessageHash)
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace(
        "[RDBMS DB] Search for incident process instance statistics by definition with query {}",
        dbQuery);

    return executePagedQuery(
        () -> incidentMapper.processInstanceStatisticsByDefinitionCount(dbQuery),
        () -> incidentMapper.processInstanceStatisticsByDefinition(dbQuery),
        dbQuery.page(),
        dbSort);
  }
}
