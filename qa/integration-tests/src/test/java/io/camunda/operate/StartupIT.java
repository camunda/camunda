/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.util.ZeebeVersionsUtil;
import io.camunda.zeebe.client.ZeebeClient;
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

import static io.camunda.operate.util.ZeebeVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IfProfileValue(name = "spring.profiles.active", value = "docker-test")
public class StartupIT {

  private static final Logger logger = LoggerFactory.getLogger(StartupIT.class);

  public TestRestTemplate restTemplate = new TestRestTemplate();

//  values for local test:
//  private static final String OPERATE_TEST_DOCKER_IMAGE = "camunda/operate";
//  public static final String VERSION = "8.1.0-alpha4";
  private static final String OPERATE_TEST_DOCKER_IMAGE = "localhost:5000/camunda/operate";
  public static final String VERSION = "current-test";
  private TestContainerUtil testContainerUtil = new TestContainerUtil();
  private GenericContainer operateContainer;
  private TestContext testContext;

  @Test
  public void testDockerWithNonRootUser() throws Exception {
    testContext = new TestContext();
    testContainerUtil.startElasticsearch(testContext);

    testContainerUtil.startZeebe(ZeebeVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME), testContext);

    operateContainer = testContainerUtil.createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
        .withCreateContainerCmdModifier(cmd -> ((CreateContainerCmd)cmd).withUser("1000620000:0"))
        .withLogConsumer(new Slf4jLogConsumer(logger));

    String elsHost = testContext.getInternalElsHost();
    Integer elsPort = testContext.getInternalElsPort();

    operateContainer
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort));

    testContainerUtil.startOperateContainer(operateContainer, testContext);
    logger.info("************ Operate started  ************");

    // when
    ResponseEntity<String> clientConfig = restTemplate.getForEntity(
        String.format("http://%s:%s/client-config.js", testContext.getExternalOperateHost(),
            testContext.getExternalOperatePort()), String.class);

    //then
    assertThat(clientConfig.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
        clientConfig.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("text/javascript")));
    assertThat(clientConfig.getBody()).isNotNull();
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
