/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.MappingRuleDbQuery;
import io.camunda.db.rdbms.sql.MappingRuleMapper;
import io.camunda.db.rdbms.sql.columns.MappingRuleSearchColumn;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingRuleReader extends AbstractEntityReader<MappingRuleEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(MappingRuleReader.class);

  private final MappingRuleMapper mappingRuleMapper;

  public MappingRuleReader(final MappingRuleMapper mappingRuleMapper) {
    super(MappingRuleSearchColumn.values());
    this.mappingRuleMapper = mappingRuleMapper;
  }

  public Optional<MappingRuleEntity> findOne(final String mappingRuleId) {
    LOG.trace("[RDBMS DB] Search for mapping rule with mapping rule ID {}", mappingRuleId);
    final SearchQueryResult<MappingRuleEntity> queryResult =
        search(MappingRuleQuery.of(b -> b.filter(f -> f.mappingRuleId(mappingRuleId))));
    return Optional.ofNullable(queryResult.items()).flatMap(hits -> hits.stream().findFirst());
  }

  public SearchQueryResult<MappingRuleEntity> search(final MappingRuleQuery query) {
    final var dbSort = convertSort(query.sort(), MappingRuleSearchColumn.MAPPING_RULE_ID);
    final var dbQuery =
        MappingRuleDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for mapping rule with filter {}", dbQuery);
    final var totalHits = mappingRuleMapper.count(dbQuery);
    final var hits = mappingRuleMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
