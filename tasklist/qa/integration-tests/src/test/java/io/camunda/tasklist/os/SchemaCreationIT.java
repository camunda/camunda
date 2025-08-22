/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestSchemaStartup;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SchemaCreationIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private RetryOpenSearchClient retryOpenSearchClient;
  @Autowired private TestSchemaStartup schemaStartup;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Test
  public void testReplicasUpdatedWhenUpdateSchemaSettingsIsTrue() throws Exception {
    // given
    tasklistProperties.getOpenSearch().setUpdateSchemaSettings(true);
    // Set a specific number of replicas in configuration
    final int configuredReplicas = 2;
    tasklistProperties.getOpenSearch().setNumberOfReplicas(configuredReplicas);

    // when
    // Schema startup should update the settings (trigger schema creation/update)
    schemaStartup.initializeSchemaOnDemand();

    // then
    // Assert that number of replicas is updated with configuration
    assertThat(indexDescriptors).isNotEmpty();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final String replicasValue =
          retryOpenSearchClient
              .getIndexSettingsFor(indexDescriptor.getFullQualifiedName())
              .numberOfReplicas();
      assertThat(replicasValue).isEqualTo(String.valueOf(configuredReplicas));
    }
  }

  @Test
  public void testReplicasNotUpdatedWhenUpdateSchemaSettingsIsFalse() throws Exception {
    // given
    tasklistProperties.getOpenSearch().setUpdateSchemaSettings(false);
    // Set a specific number of replicas in configuration
    final int configuredReplicas = 3;
    tasklistProperties.getOpenSearch().setNumberOfReplicas(configuredReplicas);

    // when
    // Schema startup should update the settings (trigger schema creation/update)
    schemaStartup.initializeSchemaOnDemand();

    // then
    // Assert that number of replicas is updated with configuration
    assertThat(indexDescriptors).isNotEmpty();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final String replicasValue =
          retryOpenSearchClient
              .getIndexSettingsFor(indexDescriptor.getFullQualifiedName())
              .numberOfReplicas();
      assertThat(replicasValue).isEqualTo("0");
    }
  }
}
