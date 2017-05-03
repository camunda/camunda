package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProcessInstanceWriter {
  private final Logger logger = LoggerFactory.getLogger(ProcessInstanceWriter.class);

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  private SimpleDateFormat sdf;

  @PostConstruct
  public void init() {
    sdf = new SimpleDateFormat(configurationService.getDateFormat());
  }

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) throws Exception {
    logger.debug("Writing [{}] process instances to elasticsearch", processInstances.size());

    BulkRequestBuilder processInstanceBulkRequest = esclient.prepareBulk();
    for (ProcessInstanceDto procInst : processInstances) {
      addImportProcessInstanceRequest(processInstanceBulkRequest, procInst);
    }
    processInstanceBulkRequest.get();
  }

  private void addImportProcessInstanceRequest(BulkRequestBuilder bulkRequest, ProcessInstanceDto procInst) throws JsonProcessingException {
    String processInstanceId = procInst.getProcessInstanceId();
    Map<String, Object> params = new HashMap<>();
    params.put(ProcessInstanceType.START_DATE, sdf.format(procInst.getStartDate()));
    String endDate = (procInst.getEndDate() != null) ? sdf.format(procInst.getEndDate()) : null;
    params.put(ProcessInstanceType.END_DATE, endDate);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.startDate = params.startDate; ctx._source.endDate = params.endDate",
      params
    );

    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    bulkRequest.add(esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(),
        configurationService.getProcessInstanceType(),
        processInstanceId)
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent, XContentType.JSON)
    );

  }

}