/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.zeebe;

import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.service.importing.AbstractImportScheduler;
import io.camunda.optimize.service.importing.ImportMediator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZeebeImportScheduler extends AbstractImportScheduler<ZeebeConfigDto> {

  public ZeebeImportScheduler(
      final List<ImportMediator> importMediators, final ZeebeConfigDto dataImportSourceDto) {
    super(importMediators, dataImportSourceDto);
  }
}
