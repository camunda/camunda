/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

/**
 * Marker interface for aggregation queries that support pagination. This interface is used to
 * indicate that query pagination will be handled in the aggregation.
 *
 * <p>If not used, pagination is typically handled in TypedSearchQueryTransformer.
 */
public interface AggregationPaginated {}
