/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.SequenceFlowDbQuery;
import io.camunda.db.rdbms.read.mapper.SequenceFlowEntityMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.search.clients.reader.SequenceFlowReader;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequenceFlowDbReader extends AbstractEntityReader<SequenceFlowEntity>
    implements SequenceFlowReader {

  private static final Logger LOG = LoggerFactory.getLogger(SequenceFlowDbReader.class);

  private final SequenceFlowMapper sequenceFlowMapper;

  public SequenceFlowDbReader(
      final SequenceFlowMapper sequenceFlowMapper, final RdbmsReaderConfig readerConfig) {
    super(null, readerConfig);
    this.sequenceFlowMapper = sequenceFlowMapper;
  }

  public SearchQueryResult<SequenceFlowEntity> search(final SequenceFlowQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> search(
      final SequenceFlowQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Search for sequence flows with {}", query);

    final var dbSort = convertSort(query.sort());

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbQuery =
        SequenceFlowDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    final var hits =
        sequenceFlowMapper.search(dbQuery).stream()
            .map(SequenceFlowEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(hits.size(), hits, null);
  }
}
