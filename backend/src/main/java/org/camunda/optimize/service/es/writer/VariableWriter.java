package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.SimpleVariableDto;
import org.camunda.optimize.dto.optimize.VariableDto;
import org.camunda.optimize.dto.optimize.VariableValueDto;
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
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class VariableWriter {

  private final Logger logger = LoggerFactory.getLogger(VariableWriter.class);

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

  public void importVariables(List<VariableDto> variables) throws Exception {
    logger.debug("Writing [{}] variables to elasticsearch", variables.size());

    BulkRequestBuilder addVariableToProcessInstanceBulkRequest = esclient.prepareBulk();
    BulkRequestBuilder variableBulkRequest = esclient.prepareBulk();
    for (VariableDto variable : variables) {
      addImportVariableRequest(addVariableToProcessInstanceBulkRequest, variable);
      addVariableRequest(variableBulkRequest, variable);
    }
    addVariableToProcessInstanceBulkRequest.get();
    variableBulkRequest.get();
  }

  private void addVariableRequest(BulkRequestBuilder variableBulkRequest, VariableDto variable) throws JsonProcessingException {
    String variableId = variable.getId();
    variableBulkRequest.add(esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getVariableType(),
        variableId
      )
      .setSource(Collections.emptyMap())
    );
  }

  private void addImportVariableRequest(BulkRequestBuilder addVariableToProcessInstanceBulkRequest, VariableDto variable) throws IOException, ParseException {
    if (isVariableFromCaseDefinition(variable)) {
      return;
    }
    String processInstanceId = variable.getProcessInstanceId();
    SimpleVariableDto variableDto = new SimpleVariableDto();
    variableDto.setId(variable.getId());
    variableDto.setName(variable.getName());
    variableDto.setType(variable.getType());
    variableDto.setValue(parseValue(variable));
    Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    HashMap jsonMap = objectMapper.readValue(
      objectMapper.writeValueAsString(variableDto),
      HashMap.class
    );
    params.put("variable", jsonMap);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.variables.add(params.variable)",
      params
    );

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(variable.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(variable.getProcessDefinitionKey());
    procInst.setProcessInstanceId(variable.getProcessInstanceId());
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.getVariables().add(variableDto);
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    addVariableToProcessInstanceBulkRequest.add(esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(),
        configurationService.getProcessInstanceType(),
        processInstanceId)
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent, XContentType.JSON)
    );
  }

  private boolean isVariableFromCaseDefinition(VariableDto variable) {
    return variable.getProcessDefinitionId() == null;
  }

  private VariableValueDto parseValue(VariableDto e) throws ParseException {
    switch (e.getType().toLowerCase()) {
      case "string":
        return new VariableValueDto(e.getValue());
      case "integer":
        return new VariableValueDto(Integer.parseInt(e.getValue()));
      case "long":
        return new VariableValueDto(Long.parseLong(e.getValue()));
      case "short":
        return new VariableValueDto(Short.parseShort(e.getValue()));
      case "double":
        return new VariableValueDto(Double.parseDouble(e.getValue()));
      case "boolean":
        return new VariableValueDto(Boolean.parseBoolean(e.getValue()));
      case "date":
        return new VariableValueDto(sdf.parse(e.getValue()));
    }
    return new VariableValueDto(e.getValue());
  }
}
