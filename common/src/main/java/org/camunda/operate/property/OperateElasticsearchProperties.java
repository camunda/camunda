/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

public class OperateElasticsearchProperties extends ElasticsearchProperties {

  public static final String DEFAULT_INDEX_PREFIX = "operate";

  private String indexPrefix = DEFAULT_INDEX_PREFIX;

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

}
