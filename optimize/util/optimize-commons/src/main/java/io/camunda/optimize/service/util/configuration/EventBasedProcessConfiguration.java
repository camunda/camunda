/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.service.util.configuration.engine.EventIngestionConfiguration;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
public class EventBasedProcessConfiguration {

  private List<String> authorizedUserIds;
  private List<String> authorizedGroupIds;
  private EventImportConfiguration eventImport;
  private EventIngestionConfiguration eventIngestion;
  private IndexRolloverConfiguration eventIndexRollover;
}
