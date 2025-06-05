/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

public class Database {
  private String type = "elasticsearch";
  private ElasticSearch elasticsearch = new ElasticSearch();
  private ProcessCache processCache = new ProcessCache();

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public ElasticSearch getElasticSearch() {
    return elasticsearch;
  }

  public void setElasticSearch(ElasticSearch elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public ProcessCache getProcessCache() {
    return processCache;
  }

  public void setProcessCache(ProcessCache processCache) {
    this.processCache = processCache;
  }
}
