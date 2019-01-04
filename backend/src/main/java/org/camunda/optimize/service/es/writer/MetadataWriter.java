package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.UPDATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;


@Component
public class MetadataWriter {
  public static final String ID = "1";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public MetadataWriter(RestHighLevelClient esClient, ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void writeMetadata(MetadataDto metadataDto) {
    try {
      String source = objectMapper.writeValueAsString(metadataDto);

      IndexRequest request = new IndexRequest(getOptimizeIndexAliasForType(METADATA_TYPE), METADATA_TYPE, ID)
        .source(source, XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT) &&
        !indexResponse.getResult().getLowercase().equals(UPDATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write Optimize version to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String message = "Could not write Optimize version to Elasticsearch.";
      logger.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
