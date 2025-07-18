/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.List;

public class SecondaryStorage {

  private static final String PREFIX = "camunda.data.secondary-storage";

  private static final String ELASTICSEARCH = "elasticsearch";
  private static final List<String> ALLOWED_TYPES = List.of(ELASTICSEARCH);

  private String type = ELASTICSEARCH;
  private Elasticsearch elasticsearch = new Elasticsearch();

  public String getType() {
    if (type == null || type.isEmpty()) {
      throw new RuntimeException(PREFIX + ".type is null or empty");
    }
    if (!ALLOWED_TYPES.contains(type)) {
      throw new RuntimeException(PREFIX + ".type is invalid. Allowed types are: " + ALLOWED_TYPES);
    }

    return type;
  }

  public void setType(String type) {}

  public Elasticsearch getElasticsearch() {
    if (!ELASTICSEARCH.equals(type)) {
      throw new RuntimeException(PREFIX + ".type is not equal to " + ELASTICSEARCH);
    }

    return elasticsearch;
  }

  public void setElasticsearch(Elasticsearch elasticsearch) {
    this.elasticsearch = elasticsearch;
  }
}
