/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.property.ZeebeProperties;
import io.camunda.tasklist.util.ContainerVersionsUtil;
import io.camunda.tasklist.zeebe.ZeebeConnector;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(
    classes = {ZeebeConnector.class, TasklistProperties.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
    })
@RunWith(SpringRunner.class)
public class ZeebeConnectorSecureIT {

  private static final String CERTIFICATE_FILE = "zeebe-test-chain.cert.pem";
  private static final String PRIVATE_KEY_FILE = "zeebe-test-server.key.pem";

  private static final DockerImageName ZEEBE_DOCKER_IMAGE =
      DockerImageName.parse("camunda/zeebe")
          .withTag(
              ContainerVersionsUtil.readProperty(
                  ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME));
  @Autowired ZeebeConnector zeebeConnector;
  private final MountableFile certsDir = MountableFile.forClasspathResource("certs");

  @Rule
  public ZeebeContainer zeebeContainer =
      new ZeebeContainer(ZEEBE_DOCKER_IMAGE)
          .withFileSystemBind(certsDir.getFilesystemPath(), "/usr/local/zeebe/certs")
          .withEnv(
              Map.of(
                  "ZEEBE_BROKER_GATEWAY_SECURITY_CERTIFICATECHAINPATH",
                      "/usr/local/zeebe/certs/" + CERTIFICATE_FILE,
                  "ZEEBE_BROKER_GATEWAY_SECURITY_PRIVATEKEYPATH",
                      "/usr/local/zeebe/certs/" + PRIVATE_KEY_FILE,
                  "ZEEBE_BROKER_GATEWAY_SECURITY_ENABLED", "true"))
          // Can't use connection wait strategy because of TLS
          .waitingFor(
              new LogMessageWaitStrategy()
                  .withRegEx(".*Broker is ready!.*")
                  .withStartupTimeout(Duration.ofSeconds(101)));

  @Test
  public void shouldConnectWithTLS() {
    // given
    final ZeebeClient zeebeClient =
        zeebeConnector.newZeebeClient(
            new ZeebeProperties()
                .setGatewayAddress(zeebeContainer.getExternalGatewayAddress())
                .setSecure(true)
                .setCertificatePath(certsDir.getFilesystemPath() + "/" + CERTIFICATE_FILE));
    // when
    final List<BrokerInfo> brokerInfos =
        zeebeClient.newTopologyRequest().send().join().getBrokers();
    // then
    assertThat(brokerInfos).isNotEmpty();
  }
}
