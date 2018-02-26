package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
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

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionXmlType.PROCESS_DEFINITION_VERSION;

@Component
public class ProcessDefinitionWriter {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) throws Exception {
    logger.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);

    enrichProcessDefinitionXmlInformation(procDefs);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs)
        throws InterruptedException, java.util.concurrent.ExecutionException {
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      String id = procDef.getId();
      bulkRequest.add(esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(configurationService.getProcessDefinitionType()),
          configurationService.getProcessDefinitionType(),
          id
        )
        .setSource(objectMapper.convertValue(procDef, Map.class)));
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

  private void enrichProcessDefinitionXmlInformation(List<ProcessDefinitionOptimizeDto> procDefs) throws JsonProcessingException {

    BulkRequestBuilder processDefinitionXmlBulkRequest = esclient.prepareBulk();

    for (ProcessDefinitionOptimizeDto procDef : procDefs) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, procDef);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0) {
      BulkResponse response = processDefinitionXmlBulkRequest.get();
      if (response.hasFailures()) {
        logger.warn("There were failures while writing process definition information to the process definition xml index. " +
            "Received error message: {}",
          response.buildFailureMessage()
        );
      }
    } else {
      logger.warn("Cannot enrich process definition xml information with an empty list of process definitions.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(BulkRequestBuilder bulkRequest, ProcessDefinitionOptimizeDto procDef)
        throws JsonProcessingException {

    Map<String, Object> params = new HashMap<>();
    params.put(PROCESS_DEFINITION_KEY, procDef.getKey());
    params.put(PROCESS_DEFINITION_VERSION, procDef.getVersion());

    Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.processDefinitionKey = params.processDefinitionKey; " +
            "ctx._source.processDefinitionVersion = params.processDefinitionVersion; ",
        params
    );

    ProcessDefinitionXmlOptimizeDto newEntry = new ProcessDefinitionXmlOptimizeDto();
    newEntry.setEngine(procDef.getEngine());
    newEntry.setProcessDefinitionId(procDef.getId());
    newEntry.setProcessDefinitionKey(procDef.getKey());
    newEntry.setProcessDefinitionVersion(procDef.getVersion().toString());

    String newEntryIfAbsent = objectMapper.writeValueAsString(newEntry);

    bulkRequest.add(esclient
        .prepareUpdate(
            configurationService.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()),
            configurationService.getProcessDefinitionXmlType(),
            procDef.getId()
        )
        .setScript(updateScript)
        .setUpsert(newEntryIfAbsent, XContentType.JSON)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );
  }

}
