/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.payload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.operate.entities.variables.BooleanVariableEntity;
import org.camunda.operate.entities.variables.DoubleVariableEntity;
import org.camunda.operate.entities.variables.LongVariableEntity;
import org.camunda.operate.entities.variables.StringVariableEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PayloadUtil {

  @Autowired
  private ObjectMapper objectMapper;

  public List<StringVariableEntity> extractStringVariables(Map<String, Object> variables) {
    List<StringVariableEntity> variablesList = new ArrayList<>();
    variables.entrySet().stream().filter(e-> e.getValue() instanceof String || e.getValue() == null).forEach(e->
      variablesList.add(new StringVariableEntity(e.getKey(), (String)e.getValue()))
    );
    return variablesList;
  }
  public List<LongVariableEntity> extractLongVariables(Map<String, Object> variables) {
    List<LongVariableEntity> variablesList = new ArrayList<>();
    variables.entrySet().stream().filter(e-> e.getValue() instanceof Long).forEach(e->
      variablesList.add(new LongVariableEntity(e.getKey(), (Long)e.getValue()))
    );
    return variablesList;
  }
  public List<DoubleVariableEntity> extractDoubleVariables(Map<String, Object> variables) {
    List<DoubleVariableEntity> variablesList = new ArrayList<>();
    variables.entrySet().stream().filter(e-> e.getValue() instanceof Double).forEach(e->
      variablesList.add(new DoubleVariableEntity(e.getKey(), (Double)e.getValue()))
    );
    return variablesList;
  }
  public List<BooleanVariableEntity> extractBooleanVariables(Map<String, Object> variables) {
    List<BooleanVariableEntity> variablesList = new ArrayList<>();
    variables.entrySet().stream().filter(e-> e.getValue() instanceof Boolean).forEach(e->
      variablesList.add(new BooleanVariableEntity(e.getKey(), (Boolean) e.getValue()))
    );
    return variablesList;
  }

  public Map<String, Object> parsePayload(String payload) throws IOException {

    Map<String, Object> map = new LinkedHashMap<>();

    traverseTheTree(objectMapper.readTree(payload), map, "");

    return map;

  }

  private void traverseTheTree(JsonNode jsonNode, Map<String, Object> map, String path) {
    if (jsonNode.isValueNode()) {

      Object value = null;

      switch (jsonNode.getNodeType()) {
      case BOOLEAN:
        value = jsonNode.booleanValue();
        break;
      case NUMBER:
        switch (jsonNode.numberType()) {
        case INT:
        case LONG:
        case BIG_INTEGER:
          value = jsonNode.longValue();
          break;
        case FLOAT:
        case DOUBLE:
        case BIG_DECIMAL:
          value = jsonNode.doubleValue();
          break;
        }
        break;
      case STRING:
        value = jsonNode.textValue();
        break;
      case NULL:
        value = null;
        break;
      case BINARY:
        //TODO
        break;
      }
      map.put(path, value);

    } else if (jsonNode.isContainerNode()){
      if (jsonNode.isObject()) {
        final Iterator<String> fieldIterator = jsonNode.fieldNames();
        while(fieldIterator.hasNext()) {
          final String fieldName = fieldIterator.next();
          traverseTheTree(jsonNode.get(fieldName), map, (path.isEmpty() ? "" : path + ".") + fieldName);
        }
      } else if (jsonNode.isArray()) {
        int i = 0;
        for (JsonNode child : jsonNode) {
          traverseTheTree(child, map, path + "[" + i + "]");
          i++;
        }
      }

    }
  }

}
