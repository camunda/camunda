/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.FormDbQuery;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.columns.FormSearchColumn;
import io.camunda.search.clients.reader.FormReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormDbReader extends AbstractEntityReader<FormEntity> implements FormReader {

  private static final Logger LOG = LoggerFactory.getLogger(FormDbReader.class);

  private final FormMapper formMapper;

  public FormDbReader(final FormMapper formMapper) {
    super(FormSearchColumn.values());
    this.formMapper = formMapper;
  }

  public Optional<FormEntity> findOne(final Long formKey) {
    LOG.trace("[RDBMS DB] Search for form with form key {}", formKey);
    final SearchQueryResult<FormEntity> queryResult =
        search(FormQuery.of(b -> b.filter(f -> f.formKeys(formKey))));
    return Optional.ofNullable(queryResult.items()).flatMap(hits -> hits.stream().findFirst());
  }

  public SearchQueryResult<FormEntity> search(final FormQuery query) {
    final var dbSort = convertSort(query.sort(), FormSearchColumn.FORM_KEY);
    final var dbQuery =
        FormDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for form with filter {}", dbQuery);
    final var totalHits = formMapper.count(dbQuery);
    final var hits = formMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  @Override
  public FormEntity getByKey(final String key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(Long.parseLong(key)).orElse(null);
  }

  @Override
  public SearchQueryResult<FormEntity> search(
      final FormQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return search(query);
  }
}
