/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.service.db.filter.FilterContext;
import java.util.List;

public interface QueryFilterEnhancerES<T> {
  void addFilterToQuery(BoolQuery.Builder query, List<T> filter, FilterContext filterContext);
}
