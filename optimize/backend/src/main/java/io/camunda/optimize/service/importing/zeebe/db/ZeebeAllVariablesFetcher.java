/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.db;

import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import java.util.List;

/**
 * Fetcher for the all-variables import pipeline. Reads Zeebe variable records from the same source
 * index as {@link ZeebeVariableFetcher} but uses an independent position tracker so this pipeline
 * advances independently.
 */
public interface ZeebeAllVariablesFetcher extends ZeebeFetcher {
  List<ZeebeVariableRecordDto> getZeebeRecordsForPrefixAndPartitionFrom(
      PositionBasedImportPage positionBasedImportPage);
}
