/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.impl;

import io.camunda.search.clients.SecondaryDbQueryService;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Noop implementation in case the secondary database is not enabled on this broker. */
public class NoopSecondaryDbQueryService implements SecondaryDbQueryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoopSecondaryDbQueryService.class);

  @Override
  public SearchQueryResult<Long> queryProcessInstanceKeys(
      final ProcessInstanceFilter filter, final SearchQueryPage page) {
    LOGGER.error("Secondary DB is not enabled on this broker, cannot query process instances");

    return new SearchQueryResult<>(0, List.of(), null, null);
  }
}
