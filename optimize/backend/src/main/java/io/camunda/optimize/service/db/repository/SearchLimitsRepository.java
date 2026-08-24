/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

/**
 * Exposes search-shaping limits of the <em>active</em> database engine. Callers that size a request
 * against these limits would otherwise have to read both engines' configuration and guess which one
 * applies, which lets an irrelevant engine's setting shape a request it will never serve.
 */
public interface SearchLimitsRepository {

  /**
   * Maximum number of buckets a single aggregation may return, from the active engine's
   * configuration. Aggregations are sized at this value and silently drop buckets beyond it, so a
   * request must not ask for more groups than this.
   *
   * <p>Never returns less than 1: a configured 0 or negative value would otherwise make a caller
   * sizing work against it either produce nothing or loop forever.
   */
  int aggregationBucketLimit();

  /**
   * The index-name prefix the active engine prepends to every alias in a request ({@code optimize}
   * by default). Callers budgeting the length of a request line need the prefix itself rather than
   * its length, because the cost on the wire depends on how the characters encode, not how many
   * there are.
   */
  String indexNamePrefix();
}
