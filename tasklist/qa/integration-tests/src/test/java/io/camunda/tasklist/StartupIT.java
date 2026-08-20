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
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.resttestclient.TestRestTemplate;
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

  public TestRestTemplate restTemplate = new TestRestTemplate();
  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
  private GenericContainer tasklistContainer;
  private TestContext testContext;

  @Test
  public void testDockerWithNonRootUser() {
    testContext = new TestContext();
    testContainerUtil.startElasticsearch(testContext);

    testContainerUtil.startStandaloneBroker(testContext);

    tasklistContainer =
        testContainerUtil
            .createTasklistContainer(testContext)
            .withAccessToHost(true)
            .withExtraHost("host.testcontainers.internal", "host-gateway")
            .withCreateContainerCmdModifier(
                cmd -> ((CreateContainerCmd) cmd).withUser("1000620000:0"))
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    final String elsHost = testContext.getInternalElsHost();
    final Integer elsPort = testContext.getInternalElsPort();

    final String elasticSearchUrl = String.format("http://%s:%s", elsHost, elsPort);
    tasklistContainer
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "elasticsearch")
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", elasticSearchUrl)
        .withEnv("CAMUNDA_DATABASE_URL", elasticSearchUrl)
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITY_ENABLED", "true")
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true");

    testContainerUtil.startTasklistContainer(tasklistContainer, testContext);
    LOGGER.info("************ Tasklist started  ************");

    // when
    final ResponseEntity<String> clientConfig =
        restTemplate.getForEntity(
            String.format(
                "http://%s:%s/v2/topology",
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
