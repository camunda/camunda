/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.page.SearchQueryPage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceReader.class);

  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceReader(final ProcessInstanceMapper processInstanceMapper) {
    this.processInstanceMapper = processInstanceMapper;
  }

  public ProcessInstanceEntity findOne(final Long processInstanceKey) {
    LOG.trace("[RDBMS DB] Search for process instance with key {}", processInstanceKey);
    return processInstanceMapper.findOne(processInstanceKey);
  }

  public SearchResult search(final ProcessInstanceDbFilter filter) {
    final var sanitizedFilter = sanitizeFilter(filter);
    LOG.trace("[RDBMS DB] Search for process instance with filter {}", sanitizedFilter);
    final var totalHits = processInstanceMapper.count(sanitizedFilter);
    final var hits = processInstanceMapper.search(sanitizedFilter);
    return new SearchResult(hits, totalHits);
  }

  private ProcessInstanceDbFilter sanitizeFilter(final ProcessInstanceDbFilter filter) {
    var sanitizedFilter = filter;

    if (sanitizedFilter.page() == null) {
      sanitizedFilter = sanitizedFilter.withPage(new SearchQueryPage.Builder().build());
    } else {
      sanitizedFilter = sanitizedFilter.withPage(sanitizedFilter.page().sanitize());
    }

    return sanitizedFilter;
  }

  public record SearchResult(List<ProcessInstanceEntity> hits, Integer total) {}
}
