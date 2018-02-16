package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
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

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class UnfinishedProcessInstanceWriter {
  private final Logger logger = LoggerFactory.getLogger(UnfinishedProcessInstanceWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    logger.debug("Writing [{}] unfinished process instances to elasticsearch", processInstances.size());

    BulkRequestBuilder processInstanceBulkRequest = esclient.prepareBulk();
    BulkRequestBuilder processInstanceIdTrackerBulkRequest = esclient.prepareBulk();

    for (ProcessInstanceDto procInst : processInstances) {
      addProcessInstanceIdTrackingRequest(processInstanceIdTrackerBulkRequest, procInst);
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    BulkResponse response = processInstanceBulkRequest.get();
    if (response.hasFailures()) {
      logger.warn("There were failures while writing process instances with message: {}",
        response.buildFailureMessage()
      );
    }
    processInstanceIdTrackerBulkRequest.get();
  }

  private void addProcessInstanceIdTrackingRequest(BulkRequestBuilder processInstanceIdTrackerBulkRequest, ProcessInstanceDto dto) {
    processInstanceIdTrackerBulkRequest.add(
        esclient.prepareIndex(
            configurationService.getOptimizeIndex(configurationService.getUnfinishedProcessInstanceIdTrackingType()),
            configurationService.getUnfinishedProcessInstanceIdTrackingType(),
            dto.getProcessInstanceId()
        )
        .setSource(Collections.emptyMap())
    );
    logger.debug("writing to [{}]", configurationService.getOptimizeIndex(configurationService.getUnfinishedProcessInstanceIdTrackingType()));
  }

  private void addImportProcessInstanceRequest(BulkRequestBuilder bulkRequest, ProcessInstanceDto procInst) throws JsonProcessingException {
    String processInstanceId = procInst.getProcessInstanceId();
    Map<String, Object> params = new HashMap<>();
    params.put(ProcessInstanceType.START_DATE, dateTimeFormatter.format(procInst.getStartDate()));
    params.put(ProcessInstanceType.ENGINE, procInst.getEngine());
    params.put(ProcessInstanceType.PROCESS_DEFINITION_VERSION, procInst.getProcessDefinitionVersion());

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.startDate = params.startDate;" +
          "ctx._source.processDefinitionVersion = params.processDefinitionVersion;" +
          " ctx._source.engine = params.engine",
      params
    );

    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    bulkRequest.add(esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()),
        configurationService.getProcessInstanceType(),
        processInstanceId)
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent, XContentType.JSON)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
    );

  }

}