/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.dao.response;

import io.camunda.tasklist.entities.TasklistEntity;
import java.util.List;

public class SearchResponse<T extends TasklistEntity> implements DAOResponse {

  private boolean error;
  private List<T> hits;
  private int size;

  private SearchResponse() {}

  public SearchResponse(boolean error) {
    this(error, null);
  }

  public SearchResponse(boolean error, List<T> hits) {
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
