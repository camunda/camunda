package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.UPDATE_SUCCESSFUL_RESPONSE_RESULT;


@Component
public class MetadataWriter {
  public static final String ID = "1";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private TransportClient esclient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;

  @Autowired
  public MetadataWriter(TransportClient esclient, ConfigurationService configurationService, ObjectMapper objectMapper) {
    this.esclient = esclient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public void writeMetadata(MetadataDto metadataDto) {
    try {
      String source = objectMapper.writeValueAsString(metadataDto);
      IndexResponse indexResponse = esclient
        .prepareIndex(
          getOptimizeIndexAliasForType(ElasticsearchConstants.METADATA_TYPE),
          ElasticsearchConstants.METADATA_TYPE,
          ID
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(source, XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT) &&
            !indexResponse.getResult().getLowercase().equals(UPDATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write Optimize version to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (JsonProcessingException e) {
      String message = "Could not write Optimize version to Elasticsearch. " +
        "Error during serialization of the Optimize version.";
      logger.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
