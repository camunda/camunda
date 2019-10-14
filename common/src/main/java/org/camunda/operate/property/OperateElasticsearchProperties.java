/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

public class OperateElasticsearchProperties extends ElasticsearchProperties {

  public static final String DEFAULT_INDEX_PREFIX = "operate";
  public static final int DEFAULT_NUMBER_OF_SHARDS = 1;
  public static final int DEFAULT_NUMBER_OF_REPLICAS = 0;

  private String indexPrefix = DEFAULT_INDEX_PREFIX;

  private int templateOrder = 30;

  private int numberOfShards = DEFAULT_NUMBER_OF_SHARDS;

  private int numberOfReplicas = DEFAULT_NUMBER_OF_REPLICAS;

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public int getTemplateOrder() {
    return templateOrder;
  }

  public void setTemplateOrder(int templateOrder) {
    this.templateOrder = templateOrder;
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }

  public void setNumberOfShards(int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public int getNumberOfReplicas() {
    return numberOfReplicas;
  }

  public void setNumberOfReplicas(int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }
}
