/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.secondarydb;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;

/**
 * This service provides a way to query the secondary database.
 *
 * @apiNote This service is not intended to be used within a stream processor in the core engine, as
 *     it will block any threads/actors while the query is running. It should always be used in a
 *     separate actor decoupled from the stream processors.
 */
public interface SecondaryDbQueryService {

  /**
   * Queries the secondary database for process instance keys.
   *
   * @param filter a builder to create a filter for the query
   * @param page a builder to create a page for the query
   * @return a result page containing the process instance keys and additonal meta information
   */
  SearchQueryResult<Long> queryProcessInstanceKeys(
      ProcessInstanceFilter filter, SearchQueryPage page);
}
