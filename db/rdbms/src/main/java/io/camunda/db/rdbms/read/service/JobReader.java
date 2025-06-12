package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.JobDbQuery;
import io.camunda.db.rdbms.read.mapper.JobEntityMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.columns.JobSearchColumn;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobReader extends AbstractEntityReader<JobEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(JobReader.class);

  private final JobMapper jobMapper;

  public JobReader(final JobMapper jobMapper) {
    super(JobSearchColumn::findByProperty);
    this.jobMapper = jobMapper;
  }

  public SearchQueryResult<JobEntity> search(final JobQuery query) {
    final var dbSort = convertSort(query.sort(), JobSearchColumn.JOB_KEY);
    final var dbQuery =
        JobDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for jobs with filter {}", dbQuery);
    final var totalHits = jobMapper.count(dbQuery);
    final var hits = jobMapper.search(dbQuery).stream().map(JobEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
