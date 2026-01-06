/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.JobDbQuery;
import io.camunda.db.rdbms.read.mapper.JobEntityMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.columns.JobSearchColumn;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobDbReader extends AbstractEntityReader<JobEntity> implements JobReader {

  private static final Logger LOG = LoggerFactory.getLogger(JobDbReader.class);

  private final JobMapper jobMapper;

  public JobDbReader(final JobMapper jobMapper) {
    super(JobSearchColumn.values());
    this.jobMapper = jobMapper;
  }

  @Override
  public SearchQueryResult<JobEntity> search(
      final JobQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), JobSearchColumn.JOB_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbQuery =
        JobDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for jobs with filter {}", dbQuery);
    final var totalHits = jobMapper.count(dbQuery);
    final var hits = jobMapper.search(dbQuery).stream().map(JobEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<JobEntity> findOne(final long jobKey) {
    final var result = search(JobQuery.of(b -> b.filter(f -> f.jobKeys(jobKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<JobEntity> search(final JobQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
