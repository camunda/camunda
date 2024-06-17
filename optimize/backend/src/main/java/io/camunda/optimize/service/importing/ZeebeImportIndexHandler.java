/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.importing.page.ImportPage;

public interface ZeebeImportIndexHandler<PAGE extends ImportPage, INDEX_DTO>
    extends ImportIndexHandler<PAGE, INDEX_DTO> {

  ZeebeDataSourceDto getDataSource();
}
