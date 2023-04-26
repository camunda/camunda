/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.util.ContainerVersionsUtil;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@RunWith(SpringRunner.class)
@IfProfileValue(name = "spring.profiles.active", value = "docker-test")
public class StartupIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupIT.class);

  //  values for local test:
  //  private static final String TASKLIST_TEST_DOCKER_IMAGE = "camunda/tasklist";
  //  public static final String VERSION = "8.1.2";
  private static final String TASKLIST_TEST_DOCKER_IMAGE = "localhost:5000/camunda/tasklist";
  private static final String VERSION = "current-test";
  public TestRestTemplate restTemplate = new TestRestTemplate();
  private TestContainerUtil testContainerUtil = new TestContainerUtil();
  private GenericContainer tasklistContainer;
  private TestContext testContext;

  @Test
  public void testDockerWithNonRootUser() {
    testContext = new TestContext();
    testContainerUtil.startElasticsearch(testContext);

    testContainerUtil.startZeebe(
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME),
        testContext);

    tasklistContainer =
        testContainerUtil
            .createTasklistContainer(TASKLIST_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withCreateContainerCmdModifier(
                cmd -> ((CreateContainerCmd) cmd).withUser("1000620000:0"))
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    final String elsHost = testContext.getInternalElsHost();
    final Integer elsPort = testContext.getInternalElsPort();

    tasklistContainer
        .withEnv(
            "CAMUNDA_TASKLIST_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv(
            "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL",
            String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort));

    testContainerUtil.startTasklistContainer(tasklistContainer, testContext);
    LOGGER.info("************ Tasklist started  ************");

    // when
    final ResponseEntity<String> clientConfig =
        restTemplate.getForEntity(
            String.format(
                "http://%s:%s/client-config.js",
                testContext.getExternalTasklistHost(), testContext.getExternalTasklistPort()),
            String.class);

    // then
    assertThat(clientConfig.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
        clientConfig
            .getHeaders()
            .getContentType()
            .isCompatibleWith(MediaType.parseMediaType("text/javascript")));
    assertThat(clientConfig.getBody()).isNotNull();
  }

  @After
  public void stopContainers() {
    if (tasklistContainer != null) {
      tasklistContainer.stop();
    }
    testContainerUtil.stopElasticsearch();
    testContainerUtil.stopZeebeAndTasklist(testContext);
  }
}
