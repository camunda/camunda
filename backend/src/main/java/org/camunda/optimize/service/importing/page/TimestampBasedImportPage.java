/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class TimestampBasedImportPage implements ImportPage {

  private OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  public void setTimestampOfLastEntity(OffsetDateTime timestampOfLastEntity) {
    this.timestampOfLastEntity = timestampOfLastEntity;
  }

}
