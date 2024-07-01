/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.page;

import lombok.Data;

@Data
public class PositionBasedImportPage implements ImportPage {

  private Long position = 0L;
  private Long sequence = 0L;
  private boolean hasSeenSequenceField = false;
}
