package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DateVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.LongVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.ShortVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.StringVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.VariableInstanceDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.VariableHelper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
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
import java.util.ArrayList;
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
  private Client esclient;
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

    BulkRequestBuilder addVariablesToProcessInstanceBulkRequest = esclient.prepareBulk();
    BulkRequestBuilder variableBulkRequest = esclient.prepareBulk();

    //build map first
    Map<String, Map <String, List<VariableDto>>> processInstanceIdToTypedVariables = new HashMap<>();
    for (VariableDto variable : variables) {
      if (isVariableFromCaseDefinition(variable) || !isVariableTypeSupported(variable.getType())) {
        logger.warn("Variable [{}] is either a case definition variable or the type [{}] is not supported!");
        continue;
      }

      if (!processInstanceIdToTypedVariables.containsKey(variable.getProcessInstanceId())) {
        processInstanceIdToTypedVariables.put(variable.getProcessInstanceId(), newTypeMap());
      }
      Map<String,List<VariableDto>> typedVars = processInstanceIdToTypedVariables.get(variable.getProcessInstanceId());

      typedVars.get(variableTypeToFieldLabel(variable.getType())).add(variable);
      addVariableRequest(variableBulkRequest, variable);
    }

    for (Map.Entry<String, Map <String, List<VariableDto>>> entry : processInstanceIdToTypedVariables.entrySet()) {
      //for every process id
      //for every type execute one upsert
      addImportVariablesRequest(addVariablesToProcessInstanceBulkRequest, entry.getKey(), entry.getValue());
    }
    try {
      if (addVariablesToProcessInstanceBulkRequest.numberOfActions() != 0) {
        BulkResponse response = addVariablesToProcessInstanceBulkRequest.get();
        if (response.hasFailures()) {
          logger.warn("There were failures while writing variables with message: {}",
            response.buildFailureMessage()
          );
        }
      }
    } catch (NullPointerException e) {
      logger.error("NPE for PID [{}]" , e);
    }
    if (variableBulkRequest.numberOfActions() != 0) {
      variableBulkRequest.get();
    }
  }

  private Map<String, List<VariableDto>> newTypeMap() {
    Map<String, List<VariableDto>> result = new HashMap<>();
    for (String type : VariableHelper.allVariableTypeFieldLabels) {
      result.put(type, new ArrayList<>());
    }
    return result;
  }

  private void addImportVariablesRequest(
    BulkRequestBuilder addVariablesToProcessInstanceBulkRequest,
    String processInstanceId,
    Map<String, List<VariableDto>> typeMappedVars) throws IOException, ParseException {

    Map<String, Object> params = buildParameters(typeMappedVars);

    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      createInlineUpdateScript(typeMappedVars),
      params
    );

    String newEntryIfAbsent = getNewProcessInstanceRecordString(processInstanceId, typeMappedVars);

    if (newEntryIfAbsent != null) {
      addVariablesToProcessInstanceBulkRequest.add(esclient
          .prepareUpdate(
              configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()),
              configurationService.getProcessInstanceType(),
              processInstanceId
          )
          .setScript(updateScript)
          .setUpsert(newEntryIfAbsent, XContentType.JSON)
          .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
      );
    }

  }

  private Map<String, Object> buildParameters(Map<String, List<VariableDto>> typeMappedVars) throws IOException, ParseException {
    Map<String, Object> params = new HashMap<>();
    for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
      String typeName = typedVarsEntry.getKey();
      List jsonMap = objectMapper.readValue(
          objectMapper.writeValueAsString(mapTypeValues(typedVarsEntry.getValue())),
          List.class);
      params.put(typeName, jsonMap);
    }

    return params;
  }

  private List<VariableInstanceDto> mapTypeValues(List<VariableDto> variablesOfOneType) throws ParseException {
    List <VariableInstanceDto> result = new ArrayList();
    for (VariableDto variable : variablesOfOneType) {
      result.add(parseValue(variable));
    }
    return result;
  }

  private String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
      String typeName = typedVarsEntry.getKey();
      builder.append("ctx._source." + typeName + ".addAll(params." + typeName + ");\n");
    }
    return builder.toString();
  }

  private String getNewProcessInstanceRecordString(String processInstanceId, Map<String, List<VariableDto>> typeMappedVars) throws JsonProcessingException, ParseException {

    VariableDto variable = grabFirstOne(typeMappedVars);
    if (variable == null) {
      //all is lost, no variables to persist, should have crashed before.
      return null;
    }

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(variable.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(variable.getProcessDefinitionKey());
    procInst.setProcessInstanceId(processInstanceId);
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    for (Map.Entry<String, List<VariableDto>> entry: typeMappedVars.entrySet()) {
      for (VariableDto var : entry.getValue()) {
        procInst.addVariableInstance(parseValue(var));
      }
    }
    return objectMapper.writeValueAsString(procInst);
  }

  private VariableDto grabFirstOne(Map<String, List<VariableDto>> typeMappedVars) {
    VariableDto variable = null;
    for (Map.Entry <String, List<VariableDto>> e : typeMappedVars.entrySet()) {
      if (!e.getValue().isEmpty()) {
        variable = e.getValue().get(0);
        break;
      }
    }
    return variable;
  }


  private void addVariableRequest(BulkRequestBuilder variableBulkRequest, VariableDto variable) throws JsonProcessingException {
    String variableId = variable.getId();
    variableBulkRequest.add(esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(configurationService.getVariableType()),
        configurationService.getVariableType(),
        variableId
      )
      .setSource(Collections.emptyMap())
    );
  }

  private boolean isVariableFromCaseDefinition(VariableDto variable) {
    return variable.getProcessDefinitionId() == null;
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
