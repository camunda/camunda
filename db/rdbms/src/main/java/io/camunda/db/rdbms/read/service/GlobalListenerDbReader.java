/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.GlobalListenerDbQuery;
import io.camunda.db.rdbms.read.mapper.GlobalListenerEntityMapper;
import io.camunda.db.rdbms.sql.GlobalListenerMapper;
import io.camunda.db.rdbms.sql.columns.GlobalListenerSearchColumn;
import io.camunda.search.clients.reader.GlobalListenerReader;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.util.GlobalListenerUtil;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalListenerDbReader extends AbstractEntityReader<GlobalListenerEntity>
    implements GlobalListenerReader {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalListenerDbReader.class);

  private final GlobalListenerMapper globalListenerMapper;

  public GlobalListenerDbReader(
      final GlobalListenerMapper globalListenerMapper, final RdbmsReaderConfig rdbmsReaderConfig) {
    super(GlobalListenerSearchColumn.values(), rdbmsReaderConfig);
    this.globalListenerMapper = globalListenerMapper;
  }

  @Override
  public GlobalListenerEntity getGlobalListener(
      final String listenerId,
      final GlobalListenerType listenerType,
      final ResourceAccessChecks resourceAccessChecks) {
    return GlobalListenerEntityMapper.toEntity(
        globalListenerMapper.get(GlobalListenerUtil.generateId(listenerId, listenerType)));
  }

  @Override
  public SearchQueryResult<GlobalListenerEntity> search(
      final GlobalListenerQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), GlobalListenerSearchColumn.ID);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        GlobalListenerDbQuery.of(b -> b.filter(query.filter()).sort(dbSort).page(dbPage));

    LOG.trace("[RDBMS DB] Search for global listeners with filter {}", dbQuery);
    final var totalHits = globalListenerMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        globalListenerMapper.search(dbQuery).stream()
            .map(GlobalListenerEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
