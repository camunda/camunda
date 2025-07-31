/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.operate.webapp.zeebe.operation.adapter.ClientBasedAdapter;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.operate.webapp.zeebe.operation.process.modify.AddTokenHandler;
import io.camunda.operate.webapp.zeebe.operation.process.modify.CancelTokenHandler;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessZeebeWrapper;
import io.camunda.operate.webapp.zeebe.operation.process.modify.MoveTokenHandler;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.webapps.zeebe.StandalonePartitionSupplier;
import java.util.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Base definition for a test that requires zeebe and opensearch/elasticsearch. The test suite
 * automatically starts zeebe and search before all the tests run, and then tears it down once all
 * the tests have finished.
 */
@SpringBootTest(
    classes = {
      TestApplication.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class,
    },
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@WebAppConfiguration
@WithMockUser(DEFAULT_USER)
@TestInstance(
    TestInstance.Lifecycle
        .PER_CLASS) // Lifecycle required to use BeforeAll and AfterAll in non-static fashion
public class OperateZeebeSearchAbstractIT {

  // These are mocked so we can bypass authentication issues when connecting to zeebe and search
  @MockBean protected CamundaAuthenticationProvider camundaAuthenticationProvider;
  @MockBean protected TenantService tenantService;

  // Prevents the zeebe client from being constructed. Components that need to connect to zeebe
  // should use the one in the zeebe container manager
  @MockBean protected CamundaClient mockCamundaClient;

  @Autowired protected ZeebeContainerManager zeebeContainerManager;
  @Autowired protected SearchContainerManager searchContainerManager;
  @Autowired protected TestResourceManager testResourceManager;
  @Autowired protected TestSearchRepository testSearchRepository;
  @Autowired protected MockMvcManager mockMvcManager;

  // Used to control and clear process/import info between test suites
  @Autowired protected ProcessCache processCache;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected PartitionHolder partitionHolder;

  @Autowired protected BeanFactory beanFactory;

  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected FlowNodeInstanceReader flowNodeInstanceReader;
  protected OperateJ5Tester operateTester;
  protected OperateServicesAdapter operateServicesAdapter;

  @BeforeAll
  public void beforeAllSetup() {
    // Mocks the authentication for zeebe/search
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(
            new CamundaAuthentication(
                DEFAULT_USER,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()));
    doReturn(TenantAccess.wildcard(null)).when(tenantService).getAuthenticatedTenants();

    // Start zeebe and elasticsearch/opensearch
    zeebeContainerManager.startContainer();
    searchContainerManager.startContainer();

    final CamundaClient camundaClient = zeebeContainerManager.getClient();
    operateTester = beanFactory.getBean(OperateJ5Tester.class, camundaClient);

    // Required to keep search and zeebe from hanging between test suites
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
    importPositionHolder.scheduleImportPositionUpdateTask();

    final var partitionSupplier = new StandalonePartitionSupplier(camundaClient);
    partitionHolder.setPartitionSupplier(partitionSupplier);

    // Allows time for everything to settle and indices to show up
    zeebeStabilityDelay();

    // Implementing tests can add any additional setup needed to run once before all the tests run
    runAdditionalBeforeAllSetup();
  }

  protected void zeebeStabilityDelay() {
    try {
      // This is an arbitrary value that was picked and seems to work
      Thread.sleep(3000);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  public void beforeEach() {
    // Mocks are cleared between each test, reset the authentication mocks so interactions with
    // search don't fail
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(
            new CamundaAuthentication(
                DEFAULT_USER,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()));
    doReturn(TenantAccess.wildcard(null)).when(tenantService).getAuthenticatedTenants();

    final var camundaClient = zeebeContainerManager.getClient();
    operateServicesAdapter =
        new ClientBasedAdapter(
            camundaClient,
            new ModifyProcessZeebeWrapper(
                camundaClient,
                new AddTokenHandler(),
                new CancelTokenHandler(flowNodeInstanceReader),
                new MoveTokenHandler(flowNodeInstanceReader)));

    // Implementing tests can add any additional setup needed to run before each test
    runAdditionalBeforeEachSetup();
  }

  protected void runAdditionalBeforeAllSetup() {}

  protected void runAdditionalBeforeEachSetup() {}

  @AfterAll
  public void afterAllTeardown() {
    // Stop zeebe and search once all the test are finished
    zeebeContainerManager.stopContainer();
    searchContainerManager.stopContainer();

    // Required to keep search and zeebe from hanging between test suites
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();

    // Allows time for everything to settle and clean up before the next test starts
    zeebeStabilityDelay();

    // Implementing tests can add any additional teardown needed to run at the completion of the
    // test suite
    runAdditionalAfterAllTeardown();
  }

  public void runAdditionalAfterAllTeardown() {}
}
