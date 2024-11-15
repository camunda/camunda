/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public interface TestData {

  static ProcessEntity processEntityWithPublicFormKey(final Long id) throws IOException {
    return new ProcessEntity()
        .setId(String.valueOf(id))
        .setKey(id)
        .setVersion(1)
        .setBpmnXml(parseBpmnResourceXml("process-with-form-key-reference.bpmn"))
        .setBpmnProcessId("formProcess");
  }

  static ProcessEntity processEntityWithPublicFormId(final Long id) throws IOException {
    return new ProcessEntity()
        .setId(String.valueOf(id))
        .setKey(id)
        .setVersion(1)
        .setBpmnXml(parseBpmnResourceXml("process-with-form-id-reference.bpmn"))
        .setBpmnProcessId("formProcess");
  }

  static ProcessEntity processEntityWithoutForm(final Long id) throws IOException {
    return new ProcessEntity()
        .setId(String.valueOf(id))
        .setKey(id)
        .setVersion(1)
        .setBpmnXml(parseBpmnResourceXml("process-without-form-reference.bpmn"))
        .setBpmnProcessId("formProcess");
  }

  static String parseBpmnResourceXml(final String resourceName) throws IOException {
    final ClassLoader classLoader = TestData.class.getClassLoader();
    final Path filePath =
        Paths.get(
            Objects.requireNonNull(classLoader.getResource("migration/process/" + resourceName))
                .getPath());
    return Files.readString(filePath);
  }
}
