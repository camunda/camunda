/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.json;

import java.util.Map;

public interface JsonMapper {

  <T> T fromJson(final String json, final Class<T> resultType);

  <T, U> T fromJson(final String json, final Class<T> resultType, final Class<U> parameterType);

  Map<String, Object> fromJsonAsMap(final String json);

  Map<String, String> fromJsonAsStringMap(final String json);

  String toJson(final Object value);
}
