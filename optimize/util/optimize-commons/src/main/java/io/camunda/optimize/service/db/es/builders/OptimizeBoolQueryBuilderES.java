/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.builders;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;

public class OptimizeBoolQueryBuilderES extends BoolQuery.Builder {

  /**
   * The {@code BoolQuery} object used in #createBaseQuerySearchRequest is shared across three
   * different implementations of the #getBaseQuery method in AbstractExecutionPlanInterpreterES.
   *
   * <p>To allow modifications across these implementations, it is necessary to ensure that the
   * {@code BoolQuery} object is mutable. This mutability is crucial as each implementation may need
   * to add additional query clauses or alter the existing query structure before the final search
   * request is built.
   *
   * <p>By making the {@code BoolQuery} mutable, we enable each method to adapt the base query
   * according to its specific requirements without the need for excessive object copying or
   * recreation, thereby improving both flexibility and performance.
   */
  @Override
  protected void _checkSingleUse() {}
}
