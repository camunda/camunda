/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.queries;

import io.swagger.v3.oas.annotations.media.Schema;

public final class RangeValueFilter {

  @Schema(description = "Value equals to.")
  private Object eq;

  @Schema(description = "Value greater than.")
  private Object gt;

  @Schema(description = "Value greater than or equals to.")
  private Object gte;

  @Schema(description = "Value less than.")
  private Object lt;

  @Schema(description = "Value less than or equals to.")
  private Object lte;

  private RangeValueFilter(
      final Object eq, final Object gt, final Object gte, final Object lt, final Object lte) {
    this.eq = eq;
    this.gt = gt;
    this.gte = gte;
    this.lt = lt;
    this.lte = lte;
  }

  public RangeValueFilter() {}

  public Object getEq() {
    return eq;
  }

  public Object getGt() {
    return gt;
  }

  public Object getGte() {
    return gte;
  }

  public Object getLt() {
    return lt;
  }

  public Object getLte() {
    return lte;
  }

  public static class RangeValueFilterBuilder {
    private Object eq;

    private Object gt;

    private Object gte;

    private Object lt;

    private Object lte;

    public RangeValueFilterBuilder eq(final Object eq) {
      this.eq = eq;
      return this;
    }

    public RangeValueFilterBuilder gt(final Object gt) {
      this.gt = gt;
      return this;
    }

    public RangeValueFilterBuilder gte(final Object gte) {
      this.gte = gte;
      return this;
    }

    public RangeValueFilterBuilder lt(final Object lt) {
      this.lt = lt;
      return this;
    }

    public RangeValueFilterBuilder lte(final Object lte) {
      this.lte = lte;
      return this;
    }

    public RangeValueFilter build() {
      return new RangeValueFilter(eq, gt, gte, lt, lte);
    }
  }
}
