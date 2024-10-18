/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe;

import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.service.importing.AbstractImportScheduler;
import io.camunda.optimize.service.importing.ImportMediator;
import java.util.List;
import org.slf4j.Logger;

public class ZeebeImportScheduler extends AbstractImportScheduler<ZeebeConfigDto> {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ZeebeImportScheduler.class);

  public ZeebeImportScheduler(
      final List<ImportMediator> importMediators, final ZeebeConfigDto dataImportSourceDto) {
    super(importMediators, dataImportSourceDto);
  }
}
