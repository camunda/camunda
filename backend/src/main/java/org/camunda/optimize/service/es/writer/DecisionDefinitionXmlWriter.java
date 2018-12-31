package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

@Component
public class DecisionDefinitionXmlWriter {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionXmlWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    logger.debug("writing [{}] decision definition XMLs to ES", decisionDefinitions.size());

    final BulkRequestBuilder processDefinitionXmlBulkRequest = esclient.prepareBulk();
    for (DecisionDefinitionOptimizeDto decisionDefinition : decisionDefinitions) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, decisionDefinition);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0 ) {
      final BulkResponse response = processDefinitionXmlBulkRequest.get();
      if (response.hasFailures()) {
        logger.warn("There were failures while writing process definition xml information. " +
            "Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot import empty list of process definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(final BulkRequestBuilder bulkRequest,
                                                    final DecisionDefinitionOptimizeDto newEntryIfAbsent) {

    final Map<String, Object> params = new HashMap<>();
    params.put(DecisionDefinitionType.DECISION_DEFINITION_XML, newEntryIfAbsent.getDmn10Xml());

    final Script updateScript = buildUpdateScript(params);

    String source = null;
    try {
      source = objectMapper.writeValueAsString(newEntryIfAbsent);
    } catch (JsonProcessingException e) {
      logger.error("can't serialize to JSON", e);
    }

    bulkRequest.add(esclient
        .prepareUpdate(
            getOptimizeIndexAliasForType(ElasticsearchConstants.DECISION_DEFINITION_TYPE),
            ElasticsearchConstants.DECISION_DEFINITION_TYPE,
            newEntryIfAbsent.getId()
        )
        .setScript(updateScript)
        .setUpsert(source, XContentType.JSON)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );
  }

  private Script buildUpdateScript(final Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.dmn10Xml = params.dmn10Xml; ",
      params
    );
  }
}
