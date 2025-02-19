/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@ExtendWith(SpringExtension.class)
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = "docker-test")
public class StartupIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupIT.class);

  //  values for local test:
  // private static final String TASKLIST_TEST_DOCKER_IMAGE = "camunda/tasklist:SNAPSHOT";
  //  public static final String VERSION = "8.1.2";
  private static final String TASKLIST_TEST_DOCKER_IMAGE = "localhost:5000/camunda/tasklist";
  private static final String VERSION = "current-test";
  public TestRestTemplate restTemplate = new TestRestTemplate();
  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
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

    final String elasticSearchUrl = String.format("http://%s:%s", elsHost, elsPort);
    tasklistContainer
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", elasticSearchUrl)
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", elasticSearchUrl)
        .withEnv("CAMUNDA_DATABASE_URL", elasticSearchUrl)
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITY_ENABLED", "true");

    testContainerUtil.startTasklistContainer(tasklistContainer, VERSION, testContext);
    LOGGER.info("************ Tasklist started  ************");

    // when
    final ResponseEntity<String> clientConfig =
        restTemplate.getForEntity(
            String.format(
                "http://%s:%s/tasklist/client-config.js",
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

  @AfterEach
  public void stopContainers() {
    if (tasklistContainer != null) {
      tasklistContainer.stop();
    }
    testContainerUtil.stopElasticsearch();
    testContainerUtil.stopZeebeAndTasklist(testContext);
  }
}
