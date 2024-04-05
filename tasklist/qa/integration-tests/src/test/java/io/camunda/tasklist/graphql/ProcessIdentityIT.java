/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
@ActiveProfiles({TasklistProfileService.IDENTITY_AUTH_PROFILE, "test"})
public class ProcessIdentityIT extends IdentityTester {

  @BeforeAll
  public static void beforeClass() {
    IdentityTester.beforeClass(false);
  }

  @DynamicPropertySource
  protected static void registerProperties(DynamicPropertyRegistry registry) {
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
