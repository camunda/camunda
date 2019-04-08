/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_TYPE;

@Component
public class ImportIndexWriter {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  public ImportIndexWriter(RestHighLevelClient esClient,
                           ObjectMapper objectMapper, DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public void importIndexes(CombinedImportIndexesDto importIndexes) {
    logger.debug("Writing import index to Elasticsearch");
    BulkRequest bulkRequest = new BulkRequest();
    addAllEntitiesBasedImportIndexesToBulk(bulkRequest, importIndexes.getAllEntitiesBasedImportIndexes());
    addDefinitionBasedImportIndexesToBulk(bulkRequest, importIndexes.getDefinitionBasedIndexes());
    try {
      BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        String errorMessage = String.format(
          "There were failures while writing import indexes. " +
            "Received error message: {}",
          bulkResponse.buildFailureMessage()
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      logger.error("There were errors while writing import indexes.", e);
    }
  }

  private void addDefinitionBasedImportIndexesToBulk(
    BulkRequest bulkRequest, List<TimestampBasedImportIndexDto> importIndexesDefinitionBasedIndexes) {
    importIndexesDefinitionBasedIndexes
      .forEach(importIndex ->
        bulkRequest.add(createDefinitionBasedRequest(importIndex))
      );
  }

  private IndexRequest createDefinitionBasedRequest(TimestampBasedImportIndexDto importIndex) {
    String currentTimeStamp =
      dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    logger.debug("Writing definition based import index [{}] of type [{}] to elasticsearch",
                 currentTimeStamp, importIndex.getEsTypeIndexRefersTo()
    );
    try {
      return new IndexRequest(
        getOptimizeIndexAliasForType(TIMESTAMP_BASED_IMPORT_INDEX_TYPE),
        TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
        getId(importIndex)
      )
        .source(objectMapper.writeValueAsString(importIndex),XContentType.JSON);
    } catch (JsonProcessingException e) {
      logger.error("Was not able to write definition based import index of type [{}] to Elasticsearch. Reason: {}",
        importIndex.getEsTypeIndexRefersTo(), e);
      return new IndexRequest();
    }
  }

  private String getId(ImportIndexDto importIndex) {
    return EsHelper.constructKey(importIndex.getEsTypeIndexRefersTo(), importIndex.getEngine());
  }

  private void addAllEntitiesBasedImportIndexesToBulk(BulkRequest bulkRequest,
                                                      List<AllEntitiesBasedImportIndexDto> importIndexes) {
    importIndexes
      .forEach(importIndexDto ->
        bulkRequest.add(createAllEntitiesBasedRequest(importIndexDto))
      );
  }

  private IndexRequest createAllEntitiesBasedRequest(AllEntitiesBasedImportIndexDto importIndex) {
    logger.debug("Writing all entities based import index type [{}] to elasticsearch. " +
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
      logger.error("Was not able to write all entities based import index of type [{}] to Elasticsearch. Reason: {}",
                   importIndex.getEsTypeIndexRefersTo(), e
      );
      return new IndexRequest();
    }
  }

}
