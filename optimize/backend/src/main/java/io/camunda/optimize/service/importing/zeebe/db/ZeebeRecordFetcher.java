/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.db;

import io.camunda.optimize.dto.zeebe.ZeebeGenericRecordDto;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import java.util.List;

/**
 * Fetcher for the combined Zeebe record index. Unlike the type-specific fetchers (e.g. {@link
 * ZeebeProcessInstanceFetcher}), this fetcher reads from the single combined index that contains
 * records of all value types and returns them as {@link ZeebeGenericRecordDto} instances. Each
 * import service is then responsible for filtering by {@code valueType} and converting to its own
 * typed DTO.
 */
public interface ZeebeRecordFetcher extends ZeebeFetcher {
  List<ZeebeGenericRecordDto> getZeebeRecordsForPrefixAndPartitionFrom(
      PositionBasedImportPage positionBasedImportPage);
}
