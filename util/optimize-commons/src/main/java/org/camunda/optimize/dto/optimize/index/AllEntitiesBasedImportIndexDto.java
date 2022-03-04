/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.index;

import lombok.Data;

@Data
public class AllEntitiesBasedImportIndexDto implements EngineImportIndexDto {
  private long importIndex;
  private String esTypeIndexRefersTo;
  private String engine;
}
