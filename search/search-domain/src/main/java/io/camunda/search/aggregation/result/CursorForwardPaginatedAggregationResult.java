/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation.result;

import java.util.List;

/** Marker interface for aggregation results that expose a page of items and a cursor. */
public interface CursorForwardPaginatedAggregationResult<E> extends AggregationResultBase {

  List<E> items();

  String endCursor();
}
