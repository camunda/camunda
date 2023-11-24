/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.writer.ImportIndexWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ImportIndexWriterOS implements ImportIndexWriter {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;

  public void importIndexes(List<EngineImportIndexDto> engineImportIndexDtos) {
//        String importItemName = "import index information";
//        log.debug("Writing [{}] {} to ES.", engineImportIndexDtos.size(), importItemName);
//
//        ElasticsearchWriterUtil.doImportBulkRequestWithList(
//                osClient,
//                importItemName,
//                engineImportIndexDtos,
//                this::addImportIndexRequest,
//                configurationService.getSkipDataAfterNestedDocLimitReached()
//        );
    throw new NotImplementedException();
  }

  private void addImportIndexRequest(BulkRequest.Builder bulkRequestBuilder, OptimizeDto optimizeDto) {
//        if (optimizeDto instanceof TimestampBasedImportIndexDto timestampBasedIndexDto) {
//          bulkRequestBuilder.operations(op->op.index(createTimestampBasedRequest(timestampBasedIndexDto));
//        } else if (optimizeDto instanceof AllEntitiesBasedImportIndexDto) {
//            AllEntitiesBasedImportIndexDto entitiesBasedIndexDto = (AllEntitiesBasedImportIndexDto) optimizeDto;
//            bulkRequest.add(createAllEntitiesBasedRequest(entitiesBasedIndexDto));
//        }
    throw new NotImplementedException();
  }

  private IndexRequest.Builder<TimestampBasedImportIndexDto> createTimestampBasedRequest(TimestampBasedImportIndexDto importIndex) {
    String currentTimeStamp = dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    log.debug(
      "Writing timestamp based import index [{}] of type [{}] with execution timestamp [{}] to elasticsearch",
      currentTimeStamp, importIndex.getEsTypeIndexRefersTo(), importIndex.getLastImportExecutionTimestamp()
    );
    return new IndexRequest.Builder<TimestampBasedImportIndexDto>().index(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .id(getId(importIndex))
      .document(importIndex);

  }

  private String getId(EngineImportIndexDto importIndex) {
    return DatabaseHelper.constructKey(importIndex.getEsTypeIndexRefersTo(), importIndex.getEngine());
  }

  private IndexRequest createAllEntitiesBasedRequest(AllEntitiesBasedImportIndexDto importIndex) {
//        log.debug("Writing all entities based import index type [{}] to elasticsearch. " +
//                        "Starting from [{}]",
//                importIndex.getEsTypeIndexRefersTo(), importIndex.getImportIndex()
//        );
//        try {
//            XContentBuilder sourceToAdjust = XContentFactory.jsonBuilder()
//                    .startObject()
//                    .field(ImportIndexIndex.ENGINE, importIndex.getEngine())
//                    .field(ImportIndexIndex.IMPORT_INDEX, importIndex.getImportIndex())
//                    .endObject();
//            return new IndexRequest(IMPORT_INDEX_INDEX_NAME)
//                    .id(getId(importIndex))
//                    .source(sourceToAdjust);
//        } catch (IOException e) {
//            log.error(
//                    "Was not able to write all entities based import index of type [{}] to Elasticsearch. Reason: {}",
//                    importIndex.getEsTypeIndexRefersTo(), e
//            );
//            return new IndexRequest();
//        }
    throw new NotImplementedException();
  }

}
