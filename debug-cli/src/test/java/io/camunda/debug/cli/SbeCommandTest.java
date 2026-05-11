/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class SbeCommandTest {

  private static final String EXPECTED_CONFIGURATION =
"""
{
    "index": 88,
    "term": 70,
    "timestamp": 1759391170100,
    "force": "FALSE",
    "newMembers": [
    {
        "type": "ACTIVE",
        "updated": 1759391120782,
        "memberId": "0"
    },
    {
        "type": "ACTIVE",
        "updated": 1759391170100,
        "memberId": "1"
    }],
    "oldMembers": [
    {
        "type": "ACTIVE",
        "updated": 1759391120782,
        "memberId": "0"
    }]
}
""";

  private CommandLine commandLine;
  private StringWriter out;

  @BeforeEach
  public void setup() {
    out = new StringWriter();
    commandLine = new CommandLine(new Main()).setOut(new PrintWriter(out));
  }

  @Test
  public void shouldDecodeSbeFileProvidedWithFileOption() throws Exception {
    // given
    final var resourceUrl = getClass().getClassLoader().getResource("default-partition-1.meta");
    assertThat(resourceUrl).isNotNull();
    final var filePath = Path.of(resourceUrl.toURI());

    // when
    final int exitCode =
        commandLine.execute(
            "sbe",
            "--schema",
            schemaPath().toString(),
            "--offset",
            "1",
            "--file",
            filePath.toString());

    // then
    assertThat(exitCode).isZero();
    final var output = new ObjectMapper().readValue(out.toString(), TestRaftMetadata.class);
    assertThat(output.term()).isEqualTo(2L);
    assertThat(output.lastFlushedIndex()).isEqualTo(96L);
    assertThat(output.votedFor()).isEqualTo("0");
    assertThat(output.commitIndex()).isEqualTo(96L);
  }

  @Test
  public void shouldDecodeSbeFileProvidedAsPositionalParameter() {
    // given
    final var resourceUrl = getClass().getClassLoader().getResource("default-partition-1.conf");
    assertThat(resourceUrl).isNotNull();
    final var filePath = Path.of(resourceUrl.getPath());

    // when
    final int exitCode =
        commandLine.execute(
            "sbe", "--schema", schemaPath().toString(), "--offset", "1", filePath.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(out.toString().trim()).isEqualTo(EXPECTED_CONFIGURATION.trim());
  }

  private Path schemaPath() {
    final var schemaPath =
        Path.of(
                "..",
                "zeebe",
                "atomix",
                "cluster",
                "src",
                "main",
                "resources",
                "raft-entry-schema.xml")
            .toAbsolutePath()
            .normalize();
    assertThat(schemaPath).exists();
    return schemaPath;
  }

  record TestRaftMetadata(long term, long lastFlushedIndex, String votedFor, long commitIndex) {}
}
