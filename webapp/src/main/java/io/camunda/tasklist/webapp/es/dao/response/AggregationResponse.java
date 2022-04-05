/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.dao.response;

import java.util.List;

public class AggregationResponse implements DAOResponse {

  private boolean error;
  private List<AggregationValue> hits;
  private int size;

  private AggregationResponse() {}

  public AggregationResponse(boolean error) {
    this(error, null);
  }

  public AggregationResponse(boolean error, List<AggregationValue> hits) {
    this.error = error;
    this.hits = hits;
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
