/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

public class OperateOpensearchProperties extends OpensearchProperties {

  public static final String DEFAULT_INDEX_PREFIX = "";
  private static final int DEFAULT_NUMBER_OF_SHARDS = 1;

  private String indexPrefix = DEFAULT_INDEX_PREFIX;
  private int numberOfShards = DEFAULT_NUMBER_OF_SHARDS;

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }
}
