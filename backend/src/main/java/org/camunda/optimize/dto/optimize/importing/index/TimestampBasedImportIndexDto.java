/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing.index;

import lombok.Data;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Data
public class TimestampBasedImportIndexDto implements ImportIndexDto {

  protected OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected String esTypeIndexRefersTo;
  protected String engine;
}
