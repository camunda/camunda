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
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.type.index.ImportIndexType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_TYPE;

@AllArgsConstructor
@Component
@Slf4j
public class ImportIndexWriter {

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;
  private DateTimeFormatter dateTimeFormatter;

  public void importIndexes(List<ImportIndexDto> importIndexDtos) {
    log.debug("Writing import index to Elasticsearch");
    BulkRequest bulkRequest = new BulkRequest();
    final List<AllEntitiesBasedImportIndexDto> allEntitiesImportIndexDtos = collectSpecificImportIndexDto(
      importIndexDtos, AllEntitiesBasedImportIndexDto.class
    );
    final List<TimestampBasedImportIndexDto> timestampBasedImportIndexDtos = collectSpecificImportIndexDto(
      importIndexDtos, TimestampBasedImportIndexDto.class
    );
    addAllEntitiesBasedImportIndexesToBulk(bulkRequest, allEntitiesImportIndexDtos);
    addDefinitionBasedImportIndexesToBulk(bulkRequest, timestampBasedImportIndexDtos);
    try {
      BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        String errorMessage = String.format(
          "There were failures while writing import indexes. Received error message: %s",
          bulkResponse.buildFailureMessage()
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      log.error("There were errors while writing import indexes.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends ImportIndexDto> List<T> collectSpecificImportIndexDto(final List<ImportIndexDto> importIndexDtos,
                                                                           final Class<T> type) {
    return importIndexDtos.stream()
      .filter(type::isInstance)
      .map(v -> (T) v)
      .collect(toList());
  }

  private void addDefinitionBasedImportIndexesToBulk(
    BulkRequest bulkRequest, List<TimestampBasedImportIndexDto> importIndexesDefinitionBasedIndexes) {
    importIndexesDefinitionBasedIndexes
      .forEach(importIndex ->
                 bulkRequest.add(createDefinitionBasedRequest(importIndex))
      );
  }

  private IndexRequest createDefinitionBasedRequest(TimestampBasedImportIndexDto importIndex) {
    String currentTimeStamp = dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    log.debug(
      "Writing definition based import index [{}] of type [{}] to elasticsearch",
      currentTimeStamp, importIndex.getEsTypeIndexRefersTo()
    );
    try {
      return new IndexRequest(
        getOptimizeIndexAliasForType(TIMESTAMP_BASED_IMPORT_INDEX_TYPE),
        TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
        getId(importIndex)
      )
        .source(objectMapper.writeValueAsString(importIndex), XContentType.JSON);
    } catch (JsonProcessingException e) {
      log.error("Was not able to write definition based import index of type [{}] to Elasticsearch. Reason: {}",
                importIndex.getEsTypeIndexRefersTo(), e
      );
      return new IndexRequest();
    }
  }

  private String getId(ImportIndexDto importIndex) {
    return EsHelper.constructKey(importIndex.getEsTypeIndexRefersTo(), importIndex.getEngine());
  }

  private void addAllEntitiesBasedImportIndexesToBulk(BulkRequest bulkRequest,
                                                      List<AllEntitiesBasedImportIndexDto> importIndexes) {
    importIndexes.forEach(importIndexDto -> bulkRequest.add(createAllEntitiesBasedRequest(importIndexDto)));
  }

  private IndexRequest createAllEntitiesBasedRequest(AllEntitiesBasedImportIndexDto importIndex) {
    log.debug("Writing all entities based import index type [{}] to elasticsearch. " +
                "Starting from [{}]",
              importIndex.getEsTypeIndexRefersTo(), importIndex.getImportIndex()
    );
    try {
      XContentBuilder sourceToAdjust = XContentFactory.jsonBuilder()
        .startObject()
        .field(ImportIndexType.ENGINE, importIndex.getEngine())
        .field(ImportIndexType.IMPORT_INDEX, importIndex.getImportIndex())
        .endObject();
      return
        new IndexRequest(getOptimizeIndexAliasForType(IMPORT_INDEX_TYPE), IMPORT_INDEX_TYPE, getId(importIndex))
          .source(sourceToAdjust);
    } catch (IOException e) {
      log.error(
        "Was not able to write all entities based import index of type [{}] to Elasticsearch. Reason: {}",
        importIndex.getEsTypeIndexRefersTo(), e
      );
      return new IndexRequest();
    }
  }

}
