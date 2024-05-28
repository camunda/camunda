/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.xcontent.XContentType;

public abstract class ElasticsearchJSONUtil {

  public static Map<String, Object> readJSONFileToMap(final String classpathFilename) {
    final Map<String, Object> result;
    try (final InputStream inputStream =
        ElasticsearchJSONUtil.class.getResourceAsStream(classpathFilename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new TasklistRuntimeException(
            "Failed to find " + classpathFilename + " in classpath.");
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Failed to load file " + classpathFilename + " from classpath.", e);
    }
    return result;
  }
}
