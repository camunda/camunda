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
    public IndexSpecificSettings defaultSettings = new IndexSpecificSettings();
    public Map<String, String> replicasByIndexName = new HashMap<>();
    public Map<String, String> shardsByIndexName = new HashMap<>();
  }

  public static final class IndexSpecificSettings {
    public String numberOfShards;
    public String numberOfReplicas;
  }
}
