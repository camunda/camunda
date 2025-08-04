/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TasklistZeebeExtension;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.zeebe.PartitionHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import io.camunda.webapps.zeebe.StandalonePartitionSupplier;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.gatewayAddress = localhost:55500",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZeebeConnectorIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired private DatabaseTestExtension databaseTestExtension;

  @Autowired private TasklistZeebeExtension zeebeExtension;

  @Autowired private ZeebeImporter zeebeImporter;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private StandalonePartitionSupplier partitionSupplier;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private TestRestTemplate testRestTemplate;

  @LocalManagementPort private int managementPort;

  @AfterEach
  public void cleanup() {
    zeebeExtension.afterEach(null);
  }

  @Test
  public void testZeebeConnection() throws Exception {
    // when 1
    // no Zeebe broker is running

    // then 1
    // application context must be successfully started
    testRequest("http://localhost:" + managementPort + "/actuator/health/liveness");
    // import is working fine
    zeebeImporter.performOneRoundOfImport();
    // partition list is empty
    assertThat(partitionHolder.getPartitionIds()).isEmpty();

    // when 2
    // Zeebe is started
    startZeebe();

    // then 2
    // data import is working
    zeebeImporter.performOneRoundOfImport();
    // partition list is not empty
    assertThat(partitionHolder.getPartitionIds()).isNotEmpty();
  }

  private void testRequest(final String url) {
    final ResponseEntity<Object> entity =
        testRestTemplate.exchange(url, HttpMethod.GET, null, Object.class);
    assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
  }

  private void startZeebe() {
    zeebeExtension.beforeEach(null);
    ReflectionTestUtils.setField(partitionSupplier, "camundaClient", zeebeExtension.getClient());
  }

  @Test
  public void testRecoverAfterZeebeRestart() throws Exception {
    // when 1
    // Zeebe is started
    startZeebe();

    // then 1
    // data import is working
    zeebeImporter.performOneRoundOfImport();

    // when 2
    // Zeebe is restarted
    zeebeExtension.afterEach(null);
    zeebeExtension.beforeEach(null);

    // then 2
    // data import is still working
    zeebeImporter.performOneRoundOfImport();
  }
}
