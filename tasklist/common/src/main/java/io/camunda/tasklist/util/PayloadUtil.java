/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PayloadUtil {

  @Autowired private ObjectMapper objectMapper;

  public Map<String, Object> parsePayload(String payload) throws IOException {

    final Map<String, Object> map = new LinkedHashMap<>();

    traverseTheTree(objectMapper.readTree(payload), map, "");

    return map;
  }

  public String readJSONStringFromClasspath(String filename) {
    try (InputStream inputStream = PayloadUtil.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
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
            default:
              break;
          }
          break;
        case STRING:
          value = jsonNode.textValue();
          break;
        case NULL:
          break;
        case BINARY:
          // TODO
          break;
        default:
          break;
      }
      map.put(path, value);

    } else if (jsonNode.isContainerNode()) {
      if (jsonNode.isObject()) {
        final Iterator<String> fieldIterator = jsonNode.fieldNames();
        while (fieldIterator.hasNext()) {
          final String fieldName = fieldIterator.next();
          traverseTheTree(
              jsonNode.get(fieldName), map, (path.isEmpty() ? "" : path + ".") + fieldName);
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
