/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads BPMN 2.0 XML resources from the classpath for each process definition.
 *
 * <p>Each process has a dedicated {@code .bpmn} file under {@code bpmn/generator/<processId>.bpmn}
 * on the test classpath. This class simply reads and returns the file bytes, keeping all diagram
 * concerns in the resource files themselves.
 *
 * <p>Used as a method reference {@code ProcessBpmnBuilder::bpmn} satisfying {@link BpmnProvider}.
 */
final class ProcessBpmnBuilder {

  private static final String RESOURCE_PATH = "bpmn/generator/";

  static byte[] bpmn(final String processId) {
    final String path = RESOURCE_PATH + processId + ".bpmn";
    try (final InputStream in =
        ProcessBpmnBuilder.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalArgumentException("BPMN resource not found on classpath: " + path);
      }
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to load BPMN resource: " + path, e);
    }
  }

  private ProcessBpmnBuilder() {}
}
