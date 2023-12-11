/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.db;

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
  private Integer nestedDocumentsLimit;

  private String prefix;

}
