/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.util.configuration.engine.EventIngestionConfiguration;

import java.util.List;

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
