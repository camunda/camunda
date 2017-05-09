package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.VariableDto;
import org.camunda.optimize.dto.optimize.variable.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.variable.DateVariableDto;
import org.camunda.optimize.dto.optimize.variable.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.variable.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.variable.LongVariableDto;
import org.camunda.optimize.dto.optimize.variable.ShortVariableDto;
import org.camunda.optimize.dto.optimize.variable.StringVariableDto;
import org.camunda.optimize.dto.optimize.variable.VariableInstanceDto;
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

import static org.camunda.optimize.service.util.VariableHelper.isBooleanType;
import static org.camunda.optimize.service.util.VariableHelper.isDateType;
import static org.camunda.optimize.service.util.VariableHelper.isDoubleType;
import static org.camunda.optimize.service.util.VariableHelper.isIntegerType;
import static org.camunda.optimize.service.util.VariableHelper.isLongType;
import static org.camunda.optimize.service.util.VariableHelper.isShortType;
import static org.camunda.optimize.service.util.VariableHelper.isStringType;
import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;
import static org.camunda.optimize.service.util.VariableHelper.variableTypeToFieldLabel;

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

  private final String variableParameterName = "variable";

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
    if (isVariableFromCaseDefinition(variable) || !isVariableTypeSupported(variable.getType())) {
      return;
    }
    String processInstanceId = variable.getProcessInstanceId();
    VariableInstanceDto variableInstanceDto = parseValue(variable);
    Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    HashMap jsonMap = objectMapper.readValue(
      objectMapper.writeValueAsString(variableInstanceDto),
      HashMap.class
    );
    params.put(variableParameterName, jsonMap);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      createInlineUpdateScript(variable.getType()),
      params
    );

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(variable.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(variable.getProcessDefinitionKey());
    procInst.setProcessInstanceId(variable.getProcessInstanceId());
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.addVariableInstance(variableInstanceDto);
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

  private String createInlineUpdateScript(String type) {
    return "ctx._source." +
      variableTypeToFieldLabel(type) +
      ".add(params." +
      variableParameterName +
      ")";
  }

  private VariableInstanceDto parseValue(VariableDto e) throws ParseException {
    VariableInstanceDto variableInstanceDto;
    if (isStringType(e.getType())) {
      StringVariableDto stringVariableDto = new StringVariableDto();
      stringVariableDto.setValue(e.getValue());
      variableInstanceDto = stringVariableDto;
    } else if (isIntegerType(e.getType())) {
      IntegerVariableDto integerVariableDto = new IntegerVariableDto();
      integerVariableDto.setValue(Integer.parseInt(e.getValue()));
      variableInstanceDto = integerVariableDto;
    } else if (isLongType(e.getType())) {
      LongVariableDto longVariableDto = new LongVariableDto();
      longVariableDto.setValue(Long.parseLong(e.getValue()));
      variableInstanceDto = longVariableDto;
    } else if(isShortType(e.getType())) {
      ShortVariableDto shortVariableDto = new ShortVariableDto();
      shortVariableDto.setValue(Short.parseShort(e.getValue()));
      variableInstanceDto = shortVariableDto;
    } else if(isDoubleType(e.getType())) {
      DoubleVariableDto doubleVariableDto = new DoubleVariableDto();
      doubleVariableDto.setValue(Double.parseDouble(e.getValue()));
      variableInstanceDto = doubleVariableDto;
    } else if(isBooleanType(e.getType())) {
      BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
      booleanVariableDto.setValue(Boolean.parseBoolean(e.getValue()));
      variableInstanceDto = booleanVariableDto;
    } else if(isDateType(e.getType())) {
      DateVariableDto dateVariableDto = new DateVariableDto();
      dateVariableDto.setValue(sdf.parse(e.getValue()));
      variableInstanceDto = dateVariableDto;
    } else {
      logger.warn("Unsupported variable type [{}] if variable {}! " +
        "Interpreting this as string type instead.", e.getType(), e.getName());
      StringVariableDto stringVariableDto = new StringVariableDto();
      stringVariableDto.setValue(e.getValue());
      variableInstanceDto = stringVariableDto;
    }
    variableInstanceDto.setType(e.getType());
    variableInstanceDto.setId(e.getId());
    variableInstanceDto.setName(e.getName());
    return variableInstanceDto;
  }

}
