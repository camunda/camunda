/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.WaitStateDbQuery;
import io.camunda.db.rdbms.read.mapper.WaitStateEntityMapper;
import io.camunda.db.rdbms.sql.WaitStateMapper;
import io.camunda.db.rdbms.sql.columns.WaitStateSearchColumn;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.search.clients.reader.WaitStateReader;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.query.ElementInstanceWaitStateQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaitStateDbReader extends AbstractEntityReader<WaitStateEntity>
    implements WaitStateReader {

  private static final Logger LOG = LoggerFactory.getLogger(WaitStateDbReader.class);

  private final WaitStateMapper waitStateMapper;

  public WaitStateDbReader(
      final WaitStateMapper waitStateMapper, final RdbmsReaderConfig readerConfig) {
    super(WaitStateSearchColumn.values(), readerConfig);
    this.waitStateMapper = waitStateMapper;
  }

  @Override
  public SearchQueryResult<WaitStateEntity> search(
      final ElementInstanceWaitStateQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), WaitStateSearchColumn.ELEMENT_INSTANCE_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        WaitStateDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for wait states with filter {}", dbQuery);
    return executePagedQuery(
        () -> waitStateMapper.count(dbQuery),
        () ->
            waitStateMapper.search(dbQuery).stream().map(WaitStateEntityMapper::toEntity).toList(),
        dbPage,
        dbSort);
  }

  public Optional<WaitStateDbModel> findOne(final long waitStateKey) {
    return Optional.ofNullable(waitStateMapper.findOne(waitStateKey));
  }
}
