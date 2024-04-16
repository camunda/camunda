/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ProcessSaasIT extends TasklistZeebeIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSaasIT.class);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.cloud.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.client.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
  }

  @Test
  public void shouldReturnOnlyMostRecentVersion() throws IOException {
    final String querySimpleProcess = "multipleVersions";
    tester.deployProcess("multipleVersions.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("multipleVersions-v2.bpmn").waitUntil().processIsDeployed();

    LOGGER.info("Test query with a simple process");
    final GraphQLResponse responseQuery = tester.getAllProcesses(querySimpleProcess);
    assertTrue(responseQuery.isOk());
    assertEquals("1", responseQuery.get("$.data.processes.length()"));
    assertEquals(querySimpleProcess, responseQuery.get("$.data.processes[0].name"));
    assertEquals("2", responseQuery.get("$.data.processes[0].version"));

    LOGGER.info("Test with empty query");
    final String emptyQuery = "";
    final GraphQLResponse responseEmptyQuery = tester.getAllProcesses(emptyQuery);
    assertTrue(responseEmptyQuery.isOk());
    assertEquals("1", responseEmptyQuery.get("$.data.processes.length()"));
    assertEquals("2", responseEmptyQuery.get("$.data.processes[0].version"));
  }

  @Test
  public void shouldReturnAllProcessesBasedOnQueries() throws IOException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    LOGGER.info("Should return all processes based on empty search query");
    testProcessRetrieval("", 3);

    LOGGER.info("Should return all process based on Process named query");
    final GraphQLResponse responseProcessNamed = testProcessRetrieval("iMpLe", 1);
    assertEquals("Simple process", responseProcessNamed.get("$.data.processes[0].name"));

    LOGGER.info("Should return all process based on Process definition id query");
    final GraphQLResponse responseProcessDefinition = testProcessRetrieval("FoRm", 1);
    assertEquals(
        "userTaskFormProcess",
        responseProcessDefinition.get("$.data.processes[0].processDefinitionId"));

    LOGGER.info("Should return all process based on Process id query");
    testProcessRetrieval("2251799813685249", 1);

    LOGGER.info("Should return all process based on partial Process id query");
    testProcessRetrieval("799813685", 0);

    LOGGER.info("Should not return");
    testProcessRetrieval("shouldNotReturn", 0);
  }

  private GraphQLResponse testProcessRetrieval(String query, int expectedCount) throws IOException {
    final GraphQLResponse response = tester.getAllProcesses(query);
    assertTrue(response.isOk());
    assertEquals(String.valueOf(expectedCount), response.get("$.data.processes.length()"));
    return response;
  }
}
