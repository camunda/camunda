/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.JobDbQuery;
import io.camunda.db.rdbms.read.mapper.JobEntityMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.columns.JobSearchColumn;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobDbReader extends AbstractEntityReader<JobEntity> implements JobReader {

  private static final Logger LOG = LoggerFactory.getLogger(JobDbReader.class);

  private final JobMapper jobMapper;

  public JobDbReader(final JobMapper jobMapper, final RdbmsReaderConfig readerConfig) {
    super(JobSearchColumn.values(), readerConfig);
    this.jobMapper = jobMapper;
  }

  @Override
  public SearchQueryResult<JobEntity> search(
      final JobQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), JobSearchColumn.JOB_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        JobDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for jobs with filter {}", dbQuery);
    final var totalHits = jobMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

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
