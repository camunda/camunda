package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DateVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.LongVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.ShortVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.StringVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.value.VariableInstanceDto;
import org.camunda.optimize.service.util.VariableHelper;
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

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public abstract class VariableWriter {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected Client esclient;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected DateTimeFormatter dateTimeFormatter;

  public void importVariables(List<VariableDto> variables) throws Exception {
    logger.debug("Writing [{}] variables to elasticsearch", variables.size());

    BulkRequestBuilder addVariablesToProcessInstanceBulkRequest = esclient.prepareBulk();
    BulkRequestBuilder variableBulkRequest = esclient.prepareBulk();

    //build map first
    Map<String, Map <String, List<VariableDto>>> processInstanceIdToTypedVariables =
      groupVariablesByProcessInstanceIds(variables);

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

  private Map<String, Map<String, List<VariableDto>>> groupVariablesByProcessInstanceIds(List<VariableDto> variableUpdates) {
    Map<String, Map <String, List<VariableDto>>> processInstanceIdToTypedVariables = new HashMap<>();
    for (VariableDto variable : variableUpdates) {
      if (isVariableFromCaseDefinition(variable) || !isVariableTypeSupported(variable.getType())) {
        logger.warn("Variable [{}] is either a case definition variable or the type [{}] is not supported!");
        continue;
      }

      if (!processInstanceIdToTypedVariables.containsKey(variable.getProcessInstanceId())) {
        processInstanceIdToTypedVariables.put(variable.getProcessInstanceId(), newTypeMap());
      }
      Map<String,List<VariableDto>> typedVars = processInstanceIdToTypedVariables.get(variable.getProcessInstanceId());

      typedVars.get(variableTypeToFieldLabel(variable.getType())).add(variable);
    }
    return processInstanceIdToTypedVariables;
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
    Map<String, List<VariableDto>> typeMappedVars) throws IOException {

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

  private Map<String, Object> buildParameters(Map<String, List<VariableDto>> typeMappedVars) throws IOException {
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

  private List<VariableInstanceDto> mapTypeValues(List<VariableDto> variablesOfOneType) {
    List <VariableInstanceDto> result = new ArrayList<>();
    for (VariableDto variable : variablesOfOneType) {
      parseValue(variable)
        .ifPresent(result::add);
    }
    return result;
  }

  protected abstract String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars);

  protected abstract String getNewProcessInstanceRecordString(String processInstanceId,
                                                              Map<String, List<VariableDto>> typeMappedVars) throws JsonProcessingException;


  protected VariableDto grabFirstOne(Map<String, List<VariableDto>> typeMappedVars) {
    VariableDto variable = null;
    for (Map.Entry <String, List<VariableDto>> e : typeMappedVars.entrySet()) {
      if (!e.getValue().isEmpty()) {
        variable = e.getValue().get(0);
        break;
      }
    }
    return variable;
  }

  private boolean isVariableFromCaseDefinition(VariableDto variable) {
    return variable.getProcessDefinitionId() == null;
  }

  protected Optional<VariableInstanceDto> parseValue(VariableDto var) {
    try {
      return Optional.of(parseValueWithException(var));
    } catch (NumberFormatException ex) {
      logger.error("Could not parse variable [{}] with type [{}] and value [{}]. Skipping this variable",
        var.getName(),
        var.getType(),
        var.getValue(),
        ex);
      return Optional.empty();
    }
  }

  private VariableInstanceDto parseValueWithException(VariableDto var) {
    VariableInstanceDto variableInstanceDto;
    if (isStringType(var.getType())) {
      StringVariableDto stringVariableDto = new StringVariableDto();
      stringVariableDto.setValue(var.getValue());
      variableInstanceDto = stringVariableDto;
    } else if (isIntegerType(var.getType())) {
      IntegerVariableDto integerVariableDto = new IntegerVariableDto();
      integerVariableDto.setValue(parseInteger(var));
      variableInstanceDto = integerVariableDto;
    } else if (isLongType(var.getType())) {
      LongVariableDto longVariableDto = new LongVariableDto();
      longVariableDto.setValue(parseLong(var));
      variableInstanceDto = longVariableDto;
    } else if(isShortType(var.getType())) {
      ShortVariableDto shortVariableDto = new ShortVariableDto();
      shortVariableDto.setValue(parseShort(var));
      variableInstanceDto = shortVariableDto;
    } else if(isDoubleType(var.getType())) {
      DoubleVariableDto doubleVariableDto = new DoubleVariableDto();
      doubleVariableDto.setValue(parseDouble(var));
      variableInstanceDto = doubleVariableDto;
    } else if(isBooleanType(var.getType())) {
      BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
      booleanVariableDto.setValue(parseBoolean(var));
      variableInstanceDto = booleanVariableDto;
    } else if(isDateType(var.getType())) {
      DateVariableDto dateVariableDto = new DateVariableDto();
      try {
        OffsetDateTime offsetDateTime = objectMapper.readerFor(OffsetDateTime.class).readValue("\""+ var.getValue() + "\"");
        dateVariableDto.setValue(offsetDateTime);
      } catch (IOException error) {
        logger.debug("Could not deserialize date variable out of [{}]! Reason: {}", var.getValue(), error.getMessage());
      }
      variableInstanceDto = dateVariableDto;
    } else {
      logger.warn("Unsupported variable type [{}] if variable {}! " +
        "Interpreting this as string type instead.", var.getType(), var.getName());
      StringVariableDto stringVariableDto = new StringVariableDto();
      stringVariableDto.setValue(var.getValue());
      variableInstanceDto = stringVariableDto;
    }
    variableInstanceDto.setType(var.getType());
    variableInstanceDto.setId(var.getId());
    variableInstanceDto.setName(var.getName());
    return variableInstanceDto;
  }

  private Boolean parseBoolean(VariableDto var) {
    return var == null? null: Boolean.parseBoolean(var.getValue());
  }

  private Double parseDouble(VariableDto var) {
    return var == null? null: Double.parseDouble(var.getValue());
  }

  private Short parseShort(VariableDto var) {
    return var == null? null: Short.parseShort(var.getValue());
  }

  private Long parseLong(VariableDto var) {
    return var == null? null: Long.parseLong(var.getValue());
  }

  private Integer parseInteger(VariableDto var) {
    return var == null? null: Integer.parseInt(var.getValue());
  }

}
