/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class BusinessKeyWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importBusinessKeysForProcessInstances(List<ProcessInstanceDto> processInstanceDtos) {
    List<BusinessKeyDto> businessKeysToSave = processInstanceDtos.stream()
      .map(this::extractBusinessKey)
      .distinct()
      .collect(Collectors.toList());

    String importItemName = "business keys";
    log.debug("Writing [{}] {} to ES.", businessKeysToSave.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      businessKeysToSave.stream().distinct().collect(Collectors.toList()),
      this::addBusinessKeyBulkRequest
    );
  }

  private BusinessKeyDto extractBusinessKey(final ProcessInstanceDto processInstance) {
    return new BusinessKeyDto(processInstance.getProcessInstanceId(), processInstance.getBusinessKey());
  }

  private void addBusinessKeyBulkRequest(BulkRequest addBusinessKeysBulkRequest, BusinessKeyDto businessKeyDto) {
    try {
      final IndexRequest request = new IndexRequest(BUSINESS_KEY_INDEX_NAME)
        .id(businessKeyDto.getProcessInstanceId())
        .source(objectMapper.writeValueAsString(businessKeyDto), XContentType.JSON);
      addBusinessKeysBulkRequest.add(request);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Business Key: {}", businessKeyDto, e);
    }
  }

}
