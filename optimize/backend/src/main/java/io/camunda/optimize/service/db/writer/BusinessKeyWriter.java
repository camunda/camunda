/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface BusinessKeyWriter {

  Logger log = LoggerFactory.getLogger(BusinessKeyWriter.class);

  void deleteByProcessInstanceIds(final List<String> processInstanceIds);

  ImportRequestDto createIndexRequestForBusinessKey(
      final BusinessKeyDto businessKeyDto, final String importItemName);

  default List<ImportRequestDto> generateBusinessKeyImports(
      List<ProcessInstanceDto> processInstanceDtos) {
    List<BusinessKeyDto> businessKeysToSave =
        processInstanceDtos.stream().map(this::extractBusinessKey).distinct().toList();

    String importItemName = "business keys";
    log.debug("Creating imports for {} [{}].", businessKeysToSave.size(), importItemName);

    return businessKeysToSave.stream()
        .map(entry -> createIndexRequestForBusinessKey(entry, importItemName))
        .toList();
  }

  default BusinessKeyDto extractBusinessKey(final ProcessInstanceDto processInstance) {
    return new BusinessKeyDto(
        processInstance.getProcessInstanceId(), processInstance.getBusinessKey());
  }
}
