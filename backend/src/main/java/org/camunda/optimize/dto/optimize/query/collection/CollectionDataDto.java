/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CollectionDataDto <DATA_TYPE> {

  protected Object configuration = new HashMap<>();
  protected List<DATA_TYPE> entities = new ArrayList<>();

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Object configuration) {
    this.configuration = configuration;
  }

  public List<DATA_TYPE> getEntities() {
    return entities;
  }

  public void setEntities(List<DATA_TYPE> entities) {
    this.entities = entities;
  }
}
