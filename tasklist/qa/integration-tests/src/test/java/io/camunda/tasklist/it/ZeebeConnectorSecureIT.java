/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.property.ZeebeProperties;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.util.CertificateUtil;
import io.camunda.tasklist.zeebe.ZeebeConnector;
import io.zeebe.containers.ZeebeContainer;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(
    classes = {
      ZeebeConnector.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    },
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    })
@ExtendWith(SpringExtension.class)
public class ZeebeConnectorSecureIT {

  private static final String CERTIFICATE_FILE = "zeebe-test-chain.cert.pem";
  private static final String PRIVATE_KEY_FILE = "zeebe-test-server.key.pem";
  private static final DockerImageName ZEEBE_DOCKER_IMAGE =
      DockerImageName.parse(
              ContainerVersionsUtil.readProperty(
                  ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_REPO_PROPERTY_NAME))
          .withTag(
              ContainerVersionsUtil.readProperty(
                  ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME));
  @Autowired ZeebeConnector zeebeConnector;
  private ZeebeContainer zeebeContainer;
  private CamundaClient camundaClient;

  @Test
  public void shouldConnectWithTLS(@TempDir final File tempDir) throws Exception {
    // given
    final File certFile = new File(tempDir, CERTIFICATE_FILE);
    final File privateKeyFile = new File(tempDir, PRIVATE_KEY_FILE);
    CertificateUtil.generateRSACertificate(certFile, privateKeyFile);
    zeebeContainer =
        new ZeebeContainer(ZEEBE_DOCKER_IMAGE)
            .withCopyFileToContainer(
                MountableFile.forHostPath(tempDir.toPath(), 0755), "/usr/local/zeebe/certs")
            .withEnv(
                Map.of(
                    "ZEEBE_BROKER_GATEWAY_SECURITY_CERTIFICATECHAINPATH",
                    "/usr/local/zeebe/certs/" + CERTIFICATE_FILE,
                    "ZEEBE_BROKER_GATEWAY_SECURITY_PRIVATEKEYPATH",
                    "/usr/local/zeebe/certs/" + PRIVATE_KEY_FILE,
                    "ZEEBE_BROKER_GATEWAY_SECURITY_ENABLED",
                    "true",
                    CREATE_SCHEMA_ENV_VAR,
                    "false",
                    "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI",
                    "true"))
            // Can't use connection wait strategy because of TLS
            .waitingFor(
                new LogMessageWaitStrategy()
                    .withRegEx(".*Broker is ready!.*")
                    .withStartupTimeout(Duration.ofSeconds(101)));
    zeebeContainer.start();
    camundaClient =
        zeebeConnector.newCamundaClient(
            new ZeebeProperties()
                .setGatewayAddress(zeebeContainer.getExternalGatewayAddress())
                .setSecure(true)
                .setCertificatePath(tempDir.getCanonicalPath() + "/" + CERTIFICATE_FILE));
    // when
    final List<BrokerInfo> brokerInfos =
        camundaClient.newTopologyRequest().send().join().getBrokers();
    // then
    assertThat(brokerInfos).isNotEmpty();
  }

  @AfterEach
  public void cleanUp() {
    if (camundaClient != null) {
      camundaClient.close();
    }
    if (zeebeContainer != null) {
      zeebeContainer.stop();
    }
  }
}
