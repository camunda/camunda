package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
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

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

@Component
public class RunningProcessInstanceWriter {
  private final Logger logger = LoggerFactory.getLogger(RunningProcessInstanceWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    logger.debug("Writing [{}] running process instances to elasticsearch", processInstances.size());

    BulkRequestBuilder processInstanceBulkRequest = esclient.prepareBulk();

    for (ProcessInstanceDto procInst : processInstances) {
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    BulkResponse response = processInstanceBulkRequest.get();
    if (response.hasFailures()) {
      logger.warn("There were failures while writing running process instances with message: {}",
        response.buildFailureMessage()
      );
    }
  }

  private void addImportProcessInstanceRequest(BulkRequestBuilder bulkRequest, ProcessInstanceDto procInst) throws JsonProcessingException {
    String processInstanceId = procInst.getProcessInstanceId();
    Map<String, Object> params = new HashMap<>();
    params.put(ProcessInstanceType.START_DATE, dateTimeFormatter.format(procInst.getStartDate()));
    params.put(ProcessInstanceType.ENGINE, procInst.getEngine());
    params.put(ProcessInstanceType.PROCESS_DEFINITION_VERSION, procInst.getProcessDefinitionVersion());
    params.put(ProcessInstanceType.BUSINESS_KEY, procInst.getBusinessKey());
    params.put(ProcessInstanceType.STATE, procInst.getState());

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.startDate = params.startDate;" +
      "ctx._source.processDefinitionVersion = params.processDefinitionVersion;" +
      "ctx._source.engine = params.engine;" +
      "ctx._source.businessKey = params.businessKey;" +
      "ctx._source.state = params.state",
        params
    );

    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    bulkRequest.add(esclient
      .prepareUpdate(
        getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_INSTANCE_TYPE),
        ElasticsearchConstants.PROC_INSTANCE_TYPE,
        processInstanceId)
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent, XContentType.JSON)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );

  }

}