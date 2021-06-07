/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.page;

import lombok.Data;

@Data
public class PositionBasedImportPage implements ImportPage {

  private Long position = 0L;

}
