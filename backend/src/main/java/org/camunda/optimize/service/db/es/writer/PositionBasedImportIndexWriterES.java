/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.writer.PositionBasedImportIndexWriter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class PositionBasedImportIndexWriterES implements PositionBasedImportIndexWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public void importIndexes(List<PositionBasedImportIndexDto> importIndexDtos) {
    String importItemName = "position based import index information";
    log.debug("Writing [{}] {} to ES.", importIndexDtos.size(), importItemName);

    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      importIndexDtos,
      this::addImportIndexRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportIndexRequest(BulkRequest bulkRequest, PositionBasedImportIndexDto optimizeDto) {
    log.debug(
      "Writing position based import index of type [{}] with position [{}] to elasticsearch",
      optimizeDto.getEsTypeIndexRefersTo(), optimizeDto.getPositionOfLastEntity()
    );
    try {
      bulkRequest.add(new IndexRequest(POSITION_BASED_IMPORT_INDEX_NAME)
                        .id(DatabaseHelper.constructKey(optimizeDto.getEsTypeIndexRefersTo(), optimizeDto.getDataSource()))
                        .source(objectMapper.writeValueAsString(optimizeDto), XContentType.JSON));
    } catch (JsonProcessingException e) {
      log.error("Was not able to write position based import index of type [{}] to Elasticsearch. Reason: {}",
                optimizeDto.getEsTypeIndexRefersTo(), e
      );
    }
  }

}
