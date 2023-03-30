/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.migration.util;

import static org.junit.Assume.assumeTrue;

import io.camunda.tasklist.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@TestPropertySource(locations = "/test.properties")
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractMigrationTest {

  @Autowired protected EntityReader entityReader;

  @Autowired protected TaskTemplate taskTemplate;

  @Autowired protected VariableIndex variableIndex;

  @Autowired protected ImportPositionIndex importPositionIndex;

  @Autowired protected UserIndex userIndex;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected TestContext testContext;

  protected void assumeThatProcessIsUnderTest(String bpmnProcessId) {
    assumeTrue(testContext.getProcessesToAssert().contains(bpmnProcessId));
  }
}
