/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import jakarta.json.JsonValue;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;

public class OpensearchScriptBuilder {
  public static final String DEFAULT_SCRIPT_LANG = "painless";

  public Script getScriptWithParameters(final String script, final Map<String, Object> parameters) {
    Objects.requireNonNull(parameters, "Script Parameters must not be null");
    return new Script.Builder()
        .inline(b -> b.source(script).params(jsonParams(parameters)).lang(DEFAULT_SCRIPT_LANG))
        .build();
  }

  private Map<String, JsonData> jsonParams(final Map<String, Object> params) {
    return params.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> json(e.getValue())));
  }

  private <A> JsonData json(final A value) {
    return JsonData.of(value == null ? JsonValue.NULL : value);
  }
}
