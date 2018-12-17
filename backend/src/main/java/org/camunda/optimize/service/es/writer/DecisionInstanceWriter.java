package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class DecisionInstanceWriter {
  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceWriter.class);

  private final Client esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  @Autowired
  public DecisionInstanceWriter(final Client esClient,
                                final ObjectMapper objectMapper,
                                final DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
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
          getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE),
          DECISION_INSTANCE_TYPE,
          decisionInstanceId
        )
        .setSource(source, XContentType.JSON)
    );

  }

  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(final String decisionDefinitionKey,
                                                                               final OffsetDateTime evaluationDate) {
    logger.info(
      "Deleting decision instances for decisionDefinitionKey {} and evaluationDate past {}",
      decisionDefinitionKey, evaluationDate
    );

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(termQuery(DecisionInstanceType.DECISION_DEFINITION_KEY, decisionDefinitionKey))
        .filter(rangeQuery(DecisionInstanceType.EVALUATION_DATE_TIME).lt(dateTimeFormatter.format(evaluationDate)));
      final BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
        .source(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
        .abortOnVersionConflict(false)
        .filter(filterQuery)
        .get();

      logger.debug(
        "BulkByScrollResponse on deleting decision instances for decisionDefinitionKey {}: {}",
        decisionDefinitionKey, response
      );
      logger.info(
        "Deleted {} decision instances for decisionDefinitionKey {} and evaluationDate past {}",
        response.getDeleted(), decisionDefinitionKey, evaluationDate
      );
    } finally {
      progressReporter.stop();
    }
  }
}