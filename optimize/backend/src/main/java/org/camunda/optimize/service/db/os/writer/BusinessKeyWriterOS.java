/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.BUSINESS_KEY_INDEX_NAME;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.writer.BusinessKeyWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class BusinessKeyWriterOS implements BusinessKeyWriter {

  private final OptimizeOpenSearchClient osClient;

  @Override
  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    String importItemName = "business keys";
    osClient.doImportBulkRequestWithList(
        importItemName, processInstanceIds, this::addDeleteRequest, false, BUSINESS_KEY_INDEX_NAME);
  }

  private BulkOperation addDeleteRequest(final String processInstanceId) {
    final DeleteOperation deleteReq = new DeleteOperation.Builder().id(processInstanceId).build();

    return new BulkOperation.Builder().delete(deleteReq).build();
  }

  @Override
  public ImportRequestDto createIndexRequestForBusinessKey(
      final BusinessKeyDto businessKeyDto, final String importItemName) {
    return ImportRequestDto.builder()
        .indexName(BUSINESS_KEY_INDEX_NAME)
        .id(businessKeyDto.getProcessInstanceId())
        .source(businessKeyDto)
        .importName(importItemName)
        .type(RequestType.INDEX)
        .build();
  }
}
