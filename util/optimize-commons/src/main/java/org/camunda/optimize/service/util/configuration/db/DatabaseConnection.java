/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.camunda.optimize.service.util.configuration.ProxyConfiguration;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;

import java.util.List;

@Data
public class DatabaseConnection {

  protected Integer timeout;

  protected Integer responseConsumerBufferLimitInMb;

  private ProxyConfiguration proxy;

  protected String pathPrefix;

  protected Boolean skipHostnameVerification;

  @JsonProperty("nodes")
  protected List<DatabaseConnectionNodeConfiguration> connectionNodes;

}
