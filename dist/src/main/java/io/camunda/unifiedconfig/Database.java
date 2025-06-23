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
  private Elasticsearch elasticsearch = new Elasticsearch();
  private Bulk bulk = new Bulk();

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public Elasticsearch getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(final Elasticsearch elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public Bulk getBulk() {
    return bulk;
  }

  public void setBulk(final Bulk bulk) {
    this.bulk = bulk;
  }

  public ProcessCache getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final ProcessCache processCache) {
    this.processCache = processCache;
  }

  private ProcessCache processCache = new ProcessCache();
}
