/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class RaftMetadataCommandTest {

  CommandLine commandLine;
  StringWriter err;
  StringWriter out;

  @BeforeEach
  public void setup() {
    err = new StringWriter();
    out = new StringWriter();
    commandLine =
        new CommandLine(new Main()).setErr(new PrintWriter(err)).setOut(new PrintWriter(out));
  }

  @Test
  public void shouldReadAndPrintRaftMetadataFile() {
    // given
    final var resourceUrl =
        getClass().getClassLoader().getResource("raft-partition-partition-1.meta");
    assertThat(resourceUrl).isNotNull();
    final var filePath = Path.of(resourceUrl.getPath());

    // when
    final int exitCode = commandLine.execute("raft", "-v", "-f", filePath.toString(), "metadata");

    System.out.println(err);
    // then
    assertThat(exitCode).isZero();
    final String output = out.toString();
    assertThat(output).isNotBlank();
  }
}
