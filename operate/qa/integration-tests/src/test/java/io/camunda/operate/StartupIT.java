/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.qa.util.TestContext;
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

  public static final String VERSION = "current-test";
  private static final Logger LOGGER = LoggerFactory.getLogger(StartupIT.class);
  //  values for local test:
  //  private static final String OPERATE_TEST_DOCKER_IMAGE = "camunda/operate";
  //  public static final String VERSION = "8.1.0-alpha4";
  private static final String OPERATE_TEST_DOCKER_IMAGE = "localhost:5000/camunda/operate";
  public TestRestTemplate restTemplate = new TestRestTemplate();
  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
  private GenericContainer operateContainer;
  private TestContext testContext;

  @Test
  public void testDockerWithNonRootUser() throws Exception {
    testContext = new TestContext();
    testContainerUtil.startElasticsearch(testContext);

    testContainerUtil.startZeebe(
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME), testContext);

    operateContainer =
        testContainerUtil
            .createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withCreateContainerCmdModifier(
                cmd -> ((CreateContainerCmd) cmd).withUser("1000620000:0"))
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    final String elsHost = testContext.getInternalElsHost();
    final Integer elsPort = testContext.getInternalElsPort();

    final String elasticsearchUrl = String.format("http://%s:%s", elsHost, elsPort);
    operateContainer
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", elasticsearchUrl)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", elasticsearchUrl)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBE_COMPATIBILITY_ENABLED", "true")
        .withEnv("CAMUNDA_DATABASE_URL", elasticsearchUrl)
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
        .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");

    testContainerUtil.startOperateContainer(operateContainer, testContext);
    LOGGER.info("************ Operate started  ************");

    // when
    final ResponseEntity<String> topology =
        restTemplate.getForEntity(
            String.format(
                "http://%s:%s/v2/topology",
                testContext.getExternalOperateHost(), testContext.getExternalOperatePort()),
            String.class);

    // then
    assertThat(topology.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
        topology
            .getHeaders()
            .getContentType()
            .isCompatibleWith(MediaType.parseMediaType("text/javascript")));
    assertThat(topology.getBody()).isNotNull();
  }

  @After
  public void stopContainers() {
    if (operateContainer != null) {
      operateContainer.stop();
    }
    testContainerUtil.stopElasticsearch();
    testContainerUtil.stopZeebeAndOperate(testContext);
  }
}
