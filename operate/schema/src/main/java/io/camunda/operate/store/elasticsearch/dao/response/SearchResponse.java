/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao.response;

import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.List;

public class SearchResponse<T extends ExporterEntity> implements DAOResponse {

  private boolean error;
  private List<T> hits;
  private int size;

  private SearchResponse() {}

  public SearchResponse(final boolean error) {
    this(error, null);
  }

  public SearchResponse(final boolean error, final List<T> hits) {
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
}
