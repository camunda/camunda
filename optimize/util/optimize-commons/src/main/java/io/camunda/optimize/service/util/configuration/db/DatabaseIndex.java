/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DatabaseIndex {

  @JsonProperty("number_of_replicas")
  private Integer numberOfReplicas;

  @JsonProperty("number_of_shards")
  private Integer numberOfShards;

  @JsonProperty("refresh_interval")
  private String refreshInterval;

  @JsonProperty("nested_documents_limit")
  private Long nestedDocumentsLimit;

  private String prefix;
}
