/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import java.util.HashMap;
import java.util.Map;

public final class NoopExporterConfiguration {
  public final ElasticsearchConfig elasticsearch = new ElasticsearchConfig();

  public static final class ElasticsearchConfig {
    public String url;
    public IndexSettings defaultSettings = new IndexSettings();
    public Map<String, Integer> replicasByIndexName = new HashMap<>();
    public Map<String, Integer> shardsByIndexName = new HashMap<>();
  }

  public static final class IndexSettings {
    public Integer numberOfShards = 1;
    public Integer numberOfReplicas = 0;
  }
}
