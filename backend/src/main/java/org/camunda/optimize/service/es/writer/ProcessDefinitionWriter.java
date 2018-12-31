package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
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

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

@Component
public class ProcessDefinitionWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) throws Exception {
    logger.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs)
        throws InterruptedException, java.util.concurrent.ExecutionException {
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      String id = procDef.getId();

      Map<String, Object> params = new HashMap<>();
      params.put(ProcessDefinitionType.PROCESS_DEFINITION_KEY, procDef.getKey());
      params.put(ProcessDefinitionType.PROCESS_DEFINITION_VERSION, procDef.getVersion());
      params.put(ProcessDefinitionType.PROCESS_DEFINITION_NAME, procDef.getName());
      params.put(ProcessDefinitionType.ENGINE, procDef.getEngine());

      Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.key = params.key; " +
          "ctx._source.name = params.name; " +
          "ctx._source.engine = params.engine; " +
          "ctx._source.version = params.version; ",
        params
      );

      bulkRequest.add(esclient
        .prepareUpdate(
          getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_DEF_TYPE),
          ElasticsearchConstants.PROC_DEF_TYPE,
          id
        )
        .setScript(updateScript)
        .setUpsert(objectMapper.convertValue(procDef, Map.class))
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
      );
    }

    if (bulkRequest.numberOfActions() > 0) {
      BulkResponse response = bulkRequest.execute().get();
      if (response.hasFailures()) {
        logger.warn("There were failures while writing process definition information. " +
            "Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot import empty list of process definitions.");
    }
  }

}
