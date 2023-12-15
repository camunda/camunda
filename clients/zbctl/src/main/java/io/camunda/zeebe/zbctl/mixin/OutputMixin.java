/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.mixin;

import io.camunda.zeebe.zbctl.serde.JsonOutputFormatter;
import io.camunda.zeebe.zbctl.serde.OutputFormatter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

public final class OutputMixin {
  @Option(
      names = "--format",
      description =
          "Specifies the output format of commands. Must be one of: [${COMPLETION-CANDIDATES}]",
      defaultValue = "JSON",
      scope = ScopeType.INHERIT)
  private OutputFormat outputFormat;

  private final OutputStream output = System.out;

  public OutputFormatter formatter() {
    return outputFormat.format(writer());
  }

  public Writer writer() {
    return new OutputStreamWriter(output, StandardCharsets.UTF_8);
  }

  public enum OutputFormat {
    JSON(JsonOutputFormatter::new);

    private final Function<Writer, OutputFormatter> factory;

    OutputFormat(final Function<Writer, OutputFormatter> factory) {
      this.factory = factory;
    }

    private OutputFormatter format(final Writer writer) {
      return factory.apply(writer);
    }
  }
}
