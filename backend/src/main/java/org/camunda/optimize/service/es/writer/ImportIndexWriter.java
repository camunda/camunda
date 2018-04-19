package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.service.es.schema.type.index.ImportIndexType;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;

@Component
public class ImportIndexWriter {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public void importIndexes(CombinedImportIndexesDto importIndexes) {
    logger.debug("Writing import index to Elasticsearch");
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    addAllEntitiesBasedImportIndexesToBulk(bulkRequest, importIndexes.getAllEntitiesBasedImportIndexes());
    addDefinitionBasedImportIndexesToBulk(bulkRequest, importIndexes.getDefinitionBasedIndexes());
    bulkRequest.get();
  }

  private void addDefinitionBasedImportIndexesToBulk(
    BulkRequestBuilder bulkRequest, List<TimestampBasedImportIndexDto> importIndexesDefinitionBasedIndexes) {
    importIndexesDefinitionBasedIndexes
      .forEach(importIndex ->
        bulkRequest.add(createDefinitionBasedRequest(importIndex))
      );
  }

  private IndexRequestBuilder createDefinitionBasedRequest(TimestampBasedImportIndexDto importIndex) {
    String currentTimeStamp =
      dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    logger.debug("Writing definition based import index [{}] of type [{}] to elasticsearch",
      currentTimeStamp, importIndex.getEsTypeIndexRefersTo());
    try {
      return esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(TIMESTAMP_BASED_IMPORT_INDEX_TYPE),
          TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
          getId(importIndex)
        )
        .setSource(
          objectMapper.writeValueAsString(importIndex),
          XContentType.JSON
        );
    } catch (JsonProcessingException e) {
      logger.error("Was not able to write definition based import index of type [{}] to Elasticsearch. Reason: {}",
        importIndex.getEsTypeIndexRefersTo(), e);
      return esclient.prepareIndex();
    }
  }

  private String getId(ImportIndexDto importIndex) {
    return EsHelper.constructKey(importIndex.getEsTypeIndexRefersTo(), importIndex.getEngine());
  }

  private void addAllEntitiesBasedImportIndexesToBulk(BulkRequestBuilder bulkRequest,
                                                      List<AllEntitiesBasedImportIndexDto> importIndexes) {
    importIndexes
      .forEach(importIndexDto ->
        bulkRequest.add(createAllEntitiesBasedRequest(importIndexDto))
      );
  }

  private IndexRequestBuilder createAllEntitiesBasedRequest(AllEntitiesBasedImportIndexDto importIndex) {
    logger.debug("Writing all entities based import index type [{}] to elasticsearch. " +
        "Starting from [{}]",
      importIndex.getEsTypeIndexRefersTo(), importIndex.getImportIndex());
    try {
      return esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(configurationService.getImportIndexType()),
          configurationService.getImportIndexType(),
          getId(importIndex)
        )
        .setSource(
          XContentFactory.jsonBuilder()
            .startObject()
              .field(ImportIndexType.ENGINE, importIndex.getEngine())
              .field(ImportIndexType.IMPORT_INDEX, importIndex.getImportIndex())
            .endObject()
        );
    } catch (IOException e) {
      logger.error("Was not able to write all entities based import index of type [{}] to Elasticsearch. Reason: {}",
        importIndex.getEsTypeIndexRefersTo(), e);
      return esclient.prepareIndex();
    }
  }

}
