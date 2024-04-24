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

import static io.camunda.tasklist.Application.SPRING_THYMELEAF_PREFIX_KEY;
import static io.camunda.tasklist.Application.SPRING_THYMELEAF_PREFIX_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.IdentityTester;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.io.IOException;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
      "graphql.servlet.exception-handlers-enabled = true",
      "management.endpoints.web.exposure.include = info,prometheus,loggers,usage-metrics",
      SPRING_THYMELEAF_PREFIX_KEY + " = " + SPRING_THYMELEAF_PREFIX_VALUE,
      "server.servlet.session.cookie.name = " + TasklistURIs.COOKIE_JSESSIONID
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ActiveProfiles({TasklistProfileService.IDENTITY_AUTH_PROFILE, "tasklist", "test"})
public class ProcessIdentityIT extends IdentityTester {

  @BeforeAll
  public static void beforeClass() {
    IdentityTester.beforeClass(false);
  }

  @DynamicPropertySource
  protected static void registerProperties(final DynamicPropertyRegistry registry) {
    IdentityTester.registerProperties(registry, false);
  }

  @Test
  public void shouldReturnProcessAfterAssigningAuthorizations() throws IOException, JSONException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();

    final String querySimpleProcess = "simple";
    GraphQLResponse response;
    response = tester.getAllProcessesWithBearerAuth(querySimpleProcess, generateTasklistToken());
    assertTrue(response.isOk());
    assertEquals("0", response.get("$.data.processes.length()"));

    final String demoUserId = getDemoUserId();
    createAuthorization(
        demoUserId, "USER", "Process_1g4wt4m", "process-definition", "START_PROCESS_INSTANCE");

    response = tester.getAllProcessesWithBearerAuth(querySimpleProcess, generateTasklistToken());
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.processes.length()"));
    assertEquals("Simple process", response.get("$.data.processes[0].name"));
  }

  @Test
  public void shouldReturnAllProcessesWithWildCard() throws IOException, JSONException {
    tester.deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("simple_process_2.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("userTaskForm.bpmn").waitUntil().processIsDeployed();

    final String query = "";

    final String demoUserId = getDemoUserId();
    createAuthorization(demoUserId, "USER", "*", "process-definition", "START_PROCESS_INSTANCE");

    final GraphQLResponse response =
        tester.getAllProcessesWithBearerAuth(query, generateTasklistToken());
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.processes.length()"));
    assertEquals("Simple process", response.get("$.data.processes[0].name"));
  }
}
