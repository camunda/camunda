/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import org.camunda.optimize.service.importing.AbstractImportScheduler;
import org.camunda.optimize.service.importing.ImportMediator;

import java.util.List;

@Slf4j
public class IngestedDataImportScheduler extends AbstractImportScheduler<IngestedDataSourceDto> {

  public IngestedDataImportScheduler(final List<ImportMediator> importMediators) {
    super(importMediators, new IngestedDataSourceDto());
  }

}
