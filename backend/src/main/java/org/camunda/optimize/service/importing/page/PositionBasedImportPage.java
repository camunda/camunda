/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.page;

import lombok.Data;

@Data
public class PositionBasedImportPage implements ImportPage {

  private Long position = 0L;
  private Long sequence = 0L;
  private boolean hasSeenSequenceField = false;

}
