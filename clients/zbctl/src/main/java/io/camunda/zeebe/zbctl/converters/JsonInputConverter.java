/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.converters;

import com.google.errorprone.annotations.MustBeClosed;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter.JsonInput;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;

public final class JsonInputConverter implements ITypeConverter<JsonInput> {

  @Override
  public JsonInput convert(final String value) {
    if (value.startsWith("{")) {
      return new JsonStringInput(value);
    }

    return new JsonFileInput(Path.of(value));
  }

  private record JsonFileInput(Path path) implements JsonInput {

    @Override
    public InputStream open() throws Exception {
      return Files.newInputStream(path);
    }
  }

  private record JsonStringInput(String json) implements JsonInput {

    @Override
    public InputStream open() {
      return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
  }

  public interface JsonInput {
    @MustBeClosed
    InputStream open() throws Exception;
  }
}
