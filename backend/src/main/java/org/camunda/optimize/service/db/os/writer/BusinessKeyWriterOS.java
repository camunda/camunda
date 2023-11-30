/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.db.writer.BusinessKeyWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.db.DatabaseConstants.BUSINESS_KEY_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class BusinessKeyWriterOS implements BusinessKeyWriter {

  //todo test it
  private final OptimizeOpenSearchClient osClient;

  public List<ImportRequestDto> generateBusinessKeyImports(List<ProcessInstanceDto> processInstanceDtos) {
//        List<BusinessKeyDto> businessKeysToSave = processInstanceDtos.stream()
//                .map(this::extractBusinessKey)
//                .distinct().toList();
//
//        String importItemName = "business keys";
//        log.debug("Creating imports for {} [{}].", businessKeysToSave.size(), importItemName);
//
//        return businessKeysToSave.stream()
//                .map(this::createIndexRequestForBusinessKey)
//                .map(request -> ImportRequestOsDto.builder()
//                        .importName(importItemName)
//                        .client(osClient)
//                        .request(request)
//                        .build())
//                .collect(Collectors.toList());
    //todo will be handled in the OPT-7376
    return new ArrayList<>();
  }

  @Override
  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
//        final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
//        log.debug("Deleting [{}] business key documents by id with bulk request.", processInstanceIds.size());
//        processInstanceIds
//                .forEach(id ->
//                        bulkRequestBuilder.operations(operation
//                                -> operation.delete(del
//                                -> del.index(BUSINESS_KEY_INDEX_NAME).id(id))));
//
//
//        osClient.bulk(bulkRequestBuilder);
    //todo will be handled in the OPT-7376
  }

  private BusinessKeyDto extractBusinessKey(final ProcessInstanceDto processInstance) {
    return new BusinessKeyDto(processInstance.getProcessInstanceId(), processInstance.getBusinessKey());
  }

  private IndexRequest.Builder<BusinessKeyDto> createIndexRequestForBusinessKey(BusinessKeyDto businessKeyDto) {
    return new IndexRequest.Builder<BusinessKeyDto>()
      .index(BUSINESS_KEY_INDEX_NAME)
      .id(businessKeyDto.getProcessInstanceId())
      .document(businessKeyDto);

  }

}
