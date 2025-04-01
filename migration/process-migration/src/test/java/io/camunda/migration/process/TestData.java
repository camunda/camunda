/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
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
            Objects.requireNonNull(classLoader.getResource("migration/processes/" + resourceName))
                .getPath());
    return Files.readString(filePath);
  }

  static ImportPositionEntity completedImportPosition(final int partition) {
    return importPosition(true, partition);
  }

  static ImportPositionEntity notCompletedImportPosition(final int partition) {
    return importPosition(false, partition);
  }

  private static ImportPositionEntity importPosition(final boolean completed, final int partition) {
    return new ImportPositionEntity()
        .setId(partition + "-" + ProcessIndex.INDEX_NAME)
        .setPartitionId(partition)
        .setAliasName(ProcessIndex.INDEX_NAME)
        .setIndexName(ProcessIndex.INDEX_NAME)
        .setCompleted(completed);
  }

  /**
   * Fork of {@link ProcessIndex} but does not include form related fields to cause the update to
   * fail for ES/OS to return an error.
   */
  class MisconfiguredProcessIndex extends AbstractIndexDescriptor {

    public MisconfiguredProcessIndex(final String indexPrefix, final boolean isElasticsearch) {
      super(indexPrefix, isElasticsearch);
    }

    @Override
    public String getMappingsClasspathFilename() {
      return "/misconfigured-process-index.json";
    }

    @Override
    public String getVersion() {
      return "8.3.0";
    }

    @Override
    public String getComponentName() {
      return OPERATE.toString();
    }

    @Override
    public String getIndexName() {
      return "process";
    }
  }
}
