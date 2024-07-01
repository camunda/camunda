/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested;

import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import io.camunda.optimize.service.importing.AbstractImportScheduler;
import io.camunda.optimize.service.importing.ImportMediator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IngestedDataImportScheduler extends AbstractImportScheduler<IngestedDataSourceDto> {

  public IngestedDataImportScheduler(final List<ImportMediator> importMediators) {
    super(importMediators, new IngestedDataSourceDto());
  }
}
