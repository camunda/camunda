package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

@Component
public class DecisionDefinitionWriter {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionWriter.class);

  private final TransportClient esclient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Autowired
  public DecisionDefinitionWriter(final TransportClient esclient,
                                  final ConfigurationService configurationService,
                                  final ObjectMapper objectMapper) {
    this.esclient = esclient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public void importProcessDefinitions(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos)
    throws ExecutionException {
    logger.debug("Writing [{}] decision definitions to elasticsearch", decisionDefinitionOptimizeDtos.size());
    writeDecisionDefinitionInformation(decisionDefinitionOptimizeDtos);
  }

  private void writeDecisionDefinitionInformation(List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos) {
    final BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (DecisionDefinitionOptimizeDto decisionDefinition : decisionDefinitionOptimizeDtos) {
      final String id = decisionDefinition.getId();

      final Map<String, Object> params = new HashMap<>();
      params.put(DecisionDefinitionType.DECISION_DEFINITION_KEY, decisionDefinition.getKey());
      params.put(DecisionDefinitionType.DECISION_DEFINITION_VERSION, decisionDefinition.getVersion());
      params.put(DecisionDefinitionType.DECISION_DEFINITION_NAME, decisionDefinition.getName());
      params.put(DecisionDefinitionType.ENGINE, decisionDefinition.getEngine());

      final Script updateScript = buildUpdateScript(params);

      bulkRequest.add(
        esclient
          .prepareUpdate(
            getOptimizeIndexAliasForType(ElasticsearchConstants.DECISION_DEFINITION_TYPE),
            ElasticsearchConstants.DECISION_DEFINITION_TYPE,
            id
          )
          .setScript(updateScript)
          .setUpsert(objectMapper.convertValue(decisionDefinition, Map.class))
          .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
      );
    }

    if (bulkRequest.numberOfActions() > 0) {
      final BulkResponse response = bulkRequest
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .get();
      if (response.hasFailures()) {
        logger.warn(
          "There were failures while writing decision definition information. Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot import empty list of decision definitions.");
    }
  }

  private Script buildUpdateScript(Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.key = params.key; " +
        "ctx._source.name = params.name; " +
        "ctx._source.engine = params.engine; " +
        "ctx._source.version = params.version; ",
      params
    );
  }

}
