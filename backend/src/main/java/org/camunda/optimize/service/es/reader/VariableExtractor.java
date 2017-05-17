package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.VariableHelper;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.reader.VariableReader.*;

public class VariableExtractor {

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

  public List<GetVariablesResponseDto> extractVariables(Aggregations aggregations) {
    List<GetVariablesResponseDto> getVariablesResponseList = new ArrayList<>();
    for (String variableFieldLabel : VariableHelper.getAllVariableTypeFieldLabels()) {
      getVariablesResponseList.addAll(extractVariablesFromType(aggregations, variableFieldLabel));
    }
    return getVariablesResponseList;
  }

  private List<GetVariablesResponseDto> extractVariablesFromType(Aggregations aggregations, String variableFieldLabel) {
    Nested stringVariables = aggregations.get(variableFieldLabel);
    Terms nameTerms = stringVariables.getAggregations().get(NAMES_AGGREGATION);
    List<GetVariablesResponseDto> responseDtoList = new ArrayList<>();
    for (Terms.Bucket nameBucket : nameTerms.getBuckets()) {
      GetVariablesResponseDto response = new GetVariablesResponseDto();
      response.setName(nameBucket.getKeyAsString());
      response.setType(VariableHelper.fieldLabelToVariableType(variableFieldLabel));
      response.setValuesAreComplete(true);
      Terms valueTerms = nameBucket.getAggregations().get(VALUE_AGGREGATION);
      List<String> values = new ArrayList<>();
      List<Terms.Bucket> buckets = valueTerms.getBuckets();
      if(buckets.size() > maxVariableValueListSize) {
        buckets = buckets.subList(0, maxVariableValueListSize);
        response.setValuesAreComplete(false);
      }
      for (Terms.Bucket variableBucket : buckets) {
        values.add(variableBucket.getKeyAsString());
      }
      response.setValues(values);
      responseDtoList.add(response);
    }
    return responseDtoList;
  }

}
