/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
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
  private static final Logger logger = LoggerFactory.getLogger(StartupIT.class);
  //  values for local test:
  //  private static final String OPERATE_TEST_DOCKER_IMAGE = "camunda/operate";
  //  public static final String VERSION = "8.1.0-alpha4";
  private static final String OPERATE_TEST_DOCKER_IMAGE = "localhost:5000/camunda/operate";
  public TestRestTemplate restTemplate = new TestRestTemplate();
  private TestContainerUtil testContainerUtil = new TestContainerUtil();
  private GenericContainer operateContainer;
  private TestContext testContext;

  @Test
  public void testDockerWithNonRootUser() throws Exception {
    testContext = new TestContext();
    testContainerUtil.startElasticsearch(testContext);

    testContainerUtil.startZeebe(
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME), testContext);

    operateContainer =
        testContainerUtil
            .createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withCreateContainerCmdModifier(
                cmd -> ((CreateContainerCmd) cmd).withUser("1000620000:0"))
            .withLogConsumer(new Slf4jLogConsumer(logger));

    String elsHost = testContext.getInternalElsHost();
    Integer elsPort = testContext.getInternalElsPort();

    operateContainer
        .withEnv(
            "CAMUNDA_OPERATE_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv(
            "CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL",
            String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort));

    testContainerUtil.startOperateContainer(operateContainer, testContext);
    logger.info("************ Operate started  ************");

    // when
    ResponseEntity<String> clientConfig =
        restTemplate.getForEntity(
            String.format(
                "http://%s:%s/client-config.js",
                testContext.getExternalOperateHost(), testContext.getExternalOperatePort()),
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
    if (operateContainer != null) {
      operateContainer.stop();
    }
    testContainerUtil.stopElasticsearch();
    testContainerUtil.stopZeebeAndOperate(testContext);
  }
}
