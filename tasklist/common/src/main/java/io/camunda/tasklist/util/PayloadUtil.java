/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PayloadUtil {

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  public Map<String, Object> parsePayload(final String payload) {

    final Map<String, Object> map = new LinkedHashMap<>();

    try {
      traverseTheTree(objectMapper.readTree(payload), map, "");
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Failed to parse payload", e);
    }

    return map;
  }

  public String readJSONStringFromClasspath(final String filename) {
    try (final InputStream inputStream = PayloadUtil.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
  }

  private void traverseTheTree(
      final JsonNode jsonNode, final Map<String, Object> map, final String path) {
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
        for (final JsonNode child : jsonNode) {
          traverseTheTree(child, map, path + "[" + i + "]");
          i++;
        }
      }
    }
  }
}
