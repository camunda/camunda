/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.property.ZeebeProperties;
import io.camunda.tasklist.util.CertificateUtil;
import io.camunda.tasklist.zeebe.ZeebeConnector;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(
    classes = {
      ZeebeConnector.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    },
    properties = {TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"})
@ExtendWith(SpringExtension.class)
public class ZeebeConnectorSecureIT {

  private static final String CERTIFICATE_FILE = "zeebe-test-chain.cert.pem";
  private static final String PRIVATE_KEY_FILE = "zeebe-test-server.key.pem";
  @Autowired ZeebeConnector zeebeConnector;
  @Autowired TasklistProperties tasklistProperties;
  private TestStandaloneBroker broker;
  private CamundaClient camundaClient;

  @Test
  public void shouldConnectWithTLS(@TempDir final File tempDir) throws Exception {
    // given
    final File certFile = new File(tempDir, CERTIFICATE_FILE);
    final File privateKeyFile = new File(tempDir, PRIVATE_KEY_FILE);
    CertificateUtil.generateRSACertificate(certFile, privateKeyFile);
    broker =
        new TestStandaloneBroker()
            .withCreateSchema(false)
            .withGatewayEnabled(true)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getData()
                      .getSecondaryStorage()
                      .setType(
                          tasklistProperties.isElasticsearchDB()
                              ? SecondaryStorageType.elasticsearch
                              : SecondaryStorageType.opensearch);
                  cfg.getApi().getGrpc().getSsl().setEnabled(true);
                  cfg.getApi().getGrpc().getSsl().setCertificatePrivateKey(privateKeyFile);
                  cfg.getApi().getGrpc().getSsl().setCertificate(certFile);
                })
            .withSecurityConfig(cfg -> cfg.getAuthentication().setUnprotectedApi(true));
    broker.start();

    // replace loopback address with localhost for certificate match
    final String gatewayAddress =
        broker.address(TestZeebePort.GATEWAY).replace("0.0.0.0", "localhost");

    camundaClient =
        zeebeConnector.newCamundaClient(
            new ZeebeProperties()
                .setGatewayAddress(gatewayAddress)
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
    if (broker != null) {
      broker.close();
    }
  }
}
