/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema;

import org.camunda.operate.es.RetryElasticsearchClient;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.indices.UserIndex;
import org.camunda.operate.schema.indices.ProcessIndex;
import org.camunda.operate.schema.templates.IncidentTemplate;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.camunda.operate.util.CollectionUtil.map;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class) @SpringBootTest(classes = {
    TestApplicationWithNoBeans.class, OperateProperties.class,
    IndexSchemaValidator.class,
    IndexDescriptor.class,
    // Assume we have only 3 indices:
    ProcessIndex.class, UserIndex.class, IncidentTemplate.class
})
public class IndexSchemaValidatorTest {

  @MockBean
  RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  List<IndexDescriptor> indexDescriptors;

  @Autowired
  ProcessIndex processIndex;

  @Autowired
  IndexSchemaValidator indexSchemaValidator;

  @Autowired
  OperateProperties operateProperties;

  private String operatePrefix;

  private List<String> allIndexNames;

  private Set<String> newerVersions;

  private Set<String> olderVersions;

  @Before
  public void setUp() {
    operatePrefix = operateProperties.getElasticsearch().getIndexPrefix();
    allIndexNames = indexDescriptors.stream().map(IndexDescriptor::getFullQualifiedName).collect(Collectors.toList());
    newerVersions = Set.of("1.1.1", "1.1.2", "1.0.1", "1.2.3");
    olderVersions = Set.of("0.2.5" , "0.1.2");
  }

  @Test
  public void testHasAnyOperateIndices() {
    // No indices
    whenELSClientReturnsIndexNames(List.of());
    assertThat(indexSchemaValidator.hasAnyOperateIndices()).isFalse();
    // At least one operate index
    whenELSClientReturnsIndexNames(List.of(operatePrefix + "-index", "not-operate"));
    assertThat(indexSchemaValidator.hasAnyOperateIndices()).isTrue();
  }

  @Test
  public void testSchemaExists() {
    // No indices
    whenELSClientReturnsIndexNames(List.of());
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    // Only 2 operate indices
    whenELSClientReturnsIndexNames(List.of(allIndexNames.get(0), allIndexNames.get(1)));
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    // All operate indices
    whenELSClientReturnsIndexNames(allIndexNames);
    assertThat(indexSchemaValidator.schemaExists()).isTrue();
  }

  @Test
  public void testNewerVersionsForIndex() {

    // Only older versions
    whenELSClientReturnsIndexNames(versionsOf(processIndex, olderVersions));
    assertThat(indexSchemaValidator.newerVersionsForIndex(processIndex)).isEmpty();
    // Only current version
    whenELSClientReturnsIndexNames(versionsOf(processIndex, Set.of(processIndex.getVersion())));
    assertThat(indexSchemaValidator.newerVersionsForIndex(processIndex)).isEmpty();
    // Only newer versions
    whenELSClientReturnsIndexNames(versionsOf(processIndex, newerVersions));
    assertThat(indexSchemaValidator.newerVersionsForIndex(processIndex)).containsAll(newerVersions);
  }

  @Test
  public void testOlderVersionsForIndex() {
    // Only newer versions
    whenELSClientReturnsIndexNames(versionsOf(processIndex, newerVersions));
    assertThat(indexSchemaValidator.olderVersionsForIndex(processIndex)).isEmpty();
    // Only current version
    whenELSClientReturnsIndexNames(versionsOf(processIndex, Set.of(processIndex.getVersion())));
    assertThat(indexSchemaValidator.olderVersionsForIndex(processIndex)).isEmpty();
    // Only older versions
    whenELSClientReturnsIndexNames(versionsOf(processIndex, olderVersions));
    assertThat(indexSchemaValidator.olderVersionsForIndex(processIndex)).isEqualTo(olderVersions);
  }

  @Test
  public void testIsValid() {
    // No indices
    whenELSClientReturnsIndexNames(List.of());
    indexSchemaValidator.validate();

    // Current indices
    whenELSClientReturnsIndexNames(allIndexNames);
    indexSchemaValidator.validate();

    // 1 older version for index
    whenELSClientReturnsIndexNames(List.of(getFullQualifiedIndexName(processIndex, "0.9.0")));
    indexSchemaValidator.validate();
  }

  @Test
  public void testIsNotValidForMoreThanOneOlderVersion() {
    // 2 older version for index
    whenELSClientReturnsIndexNames(List.of(
        getFullQualifiedIndexName(processIndex, "0.9.0"),
        getFullQualifiedIndexName(processIndex, "0.8.0")));
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> indexSchemaValidator.validate())
        .withMessageContaining("More than one older version for process (1.0.0) found: [0.8.0, 0.9.0]");
  }

  @Test
  public void testIsNotValidForANewerVersion() {
    // 1 newer version for index
    whenELSClientReturnsIndexNames(List.of(getFullQualifiedIndexName(processIndex, "2.0.0")));
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> indexSchemaValidator.validate())
        .withMessageContaining("Newer version(s) for process (1.0.0) already exists: [2.0.0]");
  }

  private void whenELSClientReturnsIndexNames(List<String> givenIndexNames) {
    Set<String> returnValues = new HashSet<>(givenIndexNames);
    when(retryElasticsearchClient.getIndexNamesFromClusterHealth(anyString())).thenReturn(returnValues);
  }

  private List<String> versionsOf(IndexDescriptor index, Set<String> versions) {
    return map(versions, version -> getFullQualifiedIndexName(index, version));
  }

  // See AbstractIndexDescriptor::getFullQualifiedIndexName
  private String getFullQualifiedIndexName(IndexDescriptor index, String version) {
    return String.format("%s-%s-%s_", operatePrefix, index.getIndexName(), version);
  }

}
