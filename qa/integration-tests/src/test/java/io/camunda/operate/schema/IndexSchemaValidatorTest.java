/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema;

import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static io.camunda.operate.util.CollectionUtil.map;
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
    newerVersions = Set.of("100.1.1", "100.1.2", "100.0.1", "100.2.3");
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

    // two indices with one name substring of another name
    whenELSClientReturnsIndexNames(List.of(getFullQualifiedIndexName("process", "0.8.0"),
        getFullQualifiedIndexName("process-instance", "0.9.0")));
    indexSchemaValidator.validate();

    // two indices with one name substring of another name
    whenELSClientReturnsIndexNames(List.of(getFullQualifiedIndexName("operation", "0.8.0"),
        getFullQualifiedIndexName("batch-operation", "0.9.0")));
    indexSchemaValidator.validate();
  }

  @Test
  public void testIsNotValidForMoreThanOneOlderVersion() {
    // 2 older version for index
    whenELSClientReturnsIndexNames(List.of(
        getFullQualifiedIndexName(processIndex, "0.9.0"),
        getFullQualifiedIndexName(processIndex, "0.8.0")));
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> indexSchemaValidator.validate())
        .withMessageContaining("More than one older version for process (" + processIndex.getVersion() + ") found: [0.8.0, 0.9.0]");
  }

  @Test
  public void testIsNotValidForANewerVersion() {
    // 1 newer version for index
    var newerVersion = "10.0.0";
    whenELSClientReturnsIndexNames(List.of(getFullQualifiedIndexName(processIndex, newerVersion)));
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> indexSchemaValidator.validate())
        .withMessageContaining("Newer version(s) for process ("+processIndex.getVersion()+") already exists: ["+newerVersion+"]");
  }

  private void whenELSClientReturnsIndexNames(List<String> givenIndexNames) {
    Set<String> returnValues = new HashSet<>(givenIndexNames);
    when(retryElasticsearchClient.getIndexNames(anyString())).thenReturn(returnValues);
  }

  private List<String> versionsOf(IndexDescriptor index, Set<String> versions) {
    return map(versions, version -> getFullQualifiedIndexName(index, version));
  }

  // See AbstractIndexDescriptor::getFullQualifiedIndexName
  private String getFullQualifiedIndexName(IndexDescriptor index, String version) {
    return getFullQualifiedIndexName(index.getIndexName(), version);
  }

  private String getFullQualifiedIndexName(String indexNamePart, String version) {
    return String.format("%s-%s-%s_", operatePrefix, indexNamePart, version);
  }

}
