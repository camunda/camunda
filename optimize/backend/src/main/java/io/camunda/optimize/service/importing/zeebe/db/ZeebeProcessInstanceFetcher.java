/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.db;

import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import java.util.List;

public interface ZeebeProcessInstanceFetcher extends ZeebeFetcher {
  List<ZeebeProcessInstanceRecordDto> getZeebeRecordsForPrefixAndPartitionFrom(
      PositionBasedImportPage positionBasedImportPage);
}
