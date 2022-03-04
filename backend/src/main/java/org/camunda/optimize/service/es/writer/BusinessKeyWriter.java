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
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class BusinessKeyWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<ImportRequestDto> generateBusinessKeyImports(List<ProcessInstanceDto> processInstanceDtos) {
    List<BusinessKeyDto> businessKeysToSave = processInstanceDtos.stream()
      .map(this::extractBusinessKey)
      .distinct()
      .collect(Collectors.toList());

    String importItemName = "business keys";
    log.debug("Creating imports for {} [{}].", businessKeysToSave.size(), importItemName);

    return businessKeysToSave.stream()
      .map(this::createIndexRequestForBusinessKey)
      .filter(Optional::isPresent)
      .map(request -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(request.get())
        .build())
      .collect(Collectors.toList());
  }

  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest();
    log.debug("Deleting [{}] business key documents by id with bulk request.", processInstanceIds.size());
    processInstanceIds.forEach(id -> bulkRequest.add(new DeleteRequest(BUSINESS_KEY_INDEX_NAME, id)));
    ElasticsearchWriterUtil.doBulkRequest(esClient, bulkRequest, BUSINESS_KEY_INDEX_NAME, false);
  }

  private BusinessKeyDto extractBusinessKey(final ProcessInstanceDto processInstance) {
    return new BusinessKeyDto(processInstance.getProcessInstanceId(), processInstance.getBusinessKey());
  }

  private Optional<IndexRequest> createIndexRequestForBusinessKey(BusinessKeyDto businessKeyDto) {
    try {
      return Optional.of(new IndexRequest(BUSINESS_KEY_INDEX_NAME)
                           .id(businessKeyDto.getProcessInstanceId())
                           .source(objectMapper.writeValueAsString(businessKeyDto), XContentType.JSON));
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Business Key: {}", businessKeyDto, e);
      return Optional.empty();
    }
  }

}
