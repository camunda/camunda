/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao.response;

import java.util.List;

public class AggregationResponse implements DAOResponse {

  private boolean error;
  private List<AggregationValue> hits;
  private int size;
  private long sumOfTotalDocs;

  private AggregationResponse() {}

  public AggregationResponse(boolean error) {
    this(error, null, 0);
  }

  public AggregationResponse(boolean error, List<AggregationValue> hits, long sumOfTotalDocs) {
    this.error = error;
    this.hits = hits;
    this.sumOfTotalDocs = sumOfTotalDocs;
    if (hits == null) {
      size = 0;
    } else {
      size = hits.size();
    }
  }

  @Override
  public boolean hasError() {
    return error;
  }

  public List<AggregationValue> getHits() {
    return hits;
  }

  public int getSize() {
    return size;
  }

  public long getSumOfTotalDocs() {
    return sumOfTotalDocs;
  }

  public static class AggregationValue {
    private String key;
    private long count;

    public AggregationValue(String key, long count) {
      this.key = key;
      this.count = count;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public long getCount() {
      return count;
    }

    public void setCount(long count) {
      this.count = count;
    }
  }
}
