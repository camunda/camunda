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
import java.util.function.Supplier;
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

  public OutputFormatter formatter() {
    return outputFormat.format();
  }

  public enum OutputFormat {
    JSON(JsonOutputFormatter::new);

    private final Supplier<OutputFormatter> factory;

    OutputFormat(final Supplier<OutputFormatter> factory) {
      this.factory = factory;
    }

    private OutputFormatter format() {
      return factory.get();
    }
  }
}
