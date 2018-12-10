package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

@Component
public class DecisionInstanceWriter {
  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceWriter.class);

  private final Client esClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public DecisionInstanceWriter(final Client esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importDecisionInstances(List<DecisionInstanceDto> decisionInstanceDtos) throws Exception {
    logger.debug("Writing [{}] decision instances to elasticsearch", decisionInstanceDtos.size());

    BulkRequestBuilder processInstanceBulkRequest = esClient.prepareBulk();

    for (DecisionInstanceDto decisionInstanceDto : decisionInstanceDtos) {
      addImportDecisionInstanceRequest(processInstanceBulkRequest, decisionInstanceDto);
    }
    BulkResponse response = processInstanceBulkRequest.get();
    if (response.hasFailures()) {
      logger.warn(
        "There were failures while writing decision instances with message: {}", response.buildFailureMessage()
      );
    }
  }

  private void addImportDecisionInstanceRequest(final BulkRequestBuilder bulkRequest,
                                                final DecisionInstanceDto decisionInstanceDto
  ) throws JsonProcessingException {
    final String decisionInstanceId = decisionInstanceDto.getDecisionInstanceId();
    final String source = objectMapper.writeValueAsString(decisionInstanceDto);
    bulkRequest.add(
      esClient
        .prepareIndex(
          getOptimizeIndexAliasForType(ElasticsearchConstants.DECISION_INSTANCE_TYPE),
          ElasticsearchConstants.DECISION_INSTANCE_TYPE,
          decisionInstanceId
        )
        .setSource(source, XContentType.JSON)
    );

  }

}