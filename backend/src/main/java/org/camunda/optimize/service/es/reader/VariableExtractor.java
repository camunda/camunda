package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.search.SearchHit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DOUBLE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.INTEGER_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.LONG_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.SHORT_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_TYPE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_VALUE;

public class VariableExtractor {

  private final String[] variableFieldLabels =
    new String[]{STRING_VARIABLES, INTEGER_VARIABLES, LONG_VARIABLES, SHORT_VARIABLES,
      DOUBLE_VARIABLES, BOOLEAN_VARIABLES, DATE_VARIABLES};
  private int maxVariableValueListSize = 0;
  private Map<String, Map<String, GetVariablesResponseDto>> nameToTypeToVariableResponse;

  public VariableExtractor(ConfigurationService configurationService) {
    this.maxVariableValueListSize = configurationService.getMaxVariableValueListSize();
    nameToTypeToVariableResponse = new HashMap<>();
  }

  public List<GetVariablesResponseDto> getVariables() {
    List<GetVariablesResponseDto> variables = new ArrayList<>();
    for (Map<String, GetVariablesResponseDto> typeToResponse : nameToTypeToVariableResponse.values()) {
      variables.addAll(typeToResponse.values());
    }
    return variables;
  }

  public void clearVariables() {
    nameToTypeToVariableResponse.clear();
  }

  public void extractVariables(SearchHit hit) {
    for (String variableFieldLabel : variableFieldLabels) {
      extractVariables(variableFieldLabel, hit);
    }
  }

  public void extractVariables(String variableFieldLabel, SearchHit hit) {

    List<Map<String, Object>> variables = (List<Map<String, Object>>) hit.getSource().get(variableFieldLabel);
    for (Map<String, Object> variableInfo : variables) {

      String name = variableInfo.get(VARIABLE_NAME).toString();
      String type = variableInfo.get(VARIABLE_TYPE).toString();
      String value = variableInfo.get(VARIABLE_VALUE).toString();

      //Handle the hit...
      if (nameToTypeToVariableResponse.containsKey(name)) {
        Map<String, GetVariablesResponseDto> typeToResponse = nameToTypeToVariableResponse.get(name);
        if (typeToResponse.containsKey(type)) {
          addVariableValueToResponse(value, typeToResponse.get(type));
        } else {
          GetVariablesResponseDto response = createGetVariableResponse(name, type, value);
          addTypeToResponseEntry(name, type, response);
        }
      } else {
        createNameToTypeToResponseEntry(name, type, value);
      }
    }
  }

  private void addVariableValueToResponse(String value, GetVariablesResponseDto responseDto) {
    List<String> values = responseDto.getValues();
    if (values.size() < maxVariableValueListSize) {
      values.add(value);
    } else {
      responseDto.setValuesAreComplete(false);
    }
  }

  private void createNameToTypeToResponseEntry(String name, String type, String value) {
    Map<String, GetVariablesResponseDto> typeToResponse = createTypeToResponseEntry(name, type, value);
    nameToTypeToVariableResponse.put(name, typeToResponse);
  }

  private Map<String, GetVariablesResponseDto> createTypeToResponseEntry(String name, String type, String value) {
    Map<String, GetVariablesResponseDto> typeToResponse = new HashMap<>();
    GetVariablesResponseDto responseDto = new GetVariablesResponseDto();
    List<String> values = new LinkedList<>();
    values.add(value);
    responseDto.setValues(values);
    responseDto.setName(name);
    responseDto.setType(type);
    responseDto.setValuesAreComplete(true);
    typeToResponse.put(type, responseDto);
    return typeToResponse;
  }

  private GetVariablesResponseDto createGetVariableResponse(String name, String type, String value) {
    GetVariablesResponseDto response = new GetVariablesResponseDto();
    List<String> values = new LinkedList<>();
    values.add(value);
    response.setValues(values);
    response.setName(name);
    response.setType(type);
    response.setValuesAreComplete(true);
    return response;
  }

  private void addTypeToResponseEntry(String name, String type, GetVariablesResponseDto response) {
    Map<String, GetVariablesResponseDto> typeToResponse = nameToTypeToVariableResponse.get(name);
    typeToResponse.put(type, response);
  }

}
