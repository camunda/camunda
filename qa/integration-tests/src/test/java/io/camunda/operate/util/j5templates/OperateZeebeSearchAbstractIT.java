/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.client.ZeebeClient;
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

import java.util.List;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Base definition for a test that requires zeebe and opensearch/elasticsearch. The test suite automatically
 * starts zeebe and search before all the tests run, and then tears it down once all the tests have finished.
 */
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
        OperateProperties.PREFIX + ".multiTenancy.enabled = false"})
@WebAppConfiguration
@WithMockUser(DEFAULT_USER)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Lifecycle required to use BeforeAll and AfterAll in non-static fashion
public class OperateZeebeSearchAbstractIT {

  @Autowired
  protected TestSearchRepository testSearchRepository;

  // These are mocked so we can bypass authentication issues when connecting to zeebe and search
  @MockBean
  protected UserService userService;
  @MockBean
  protected TenantService tenantService;

  @Autowired
  protected ZeebeContainerManager zeebeContainerManager;
  @Autowired
  protected SearchContainerManager searchContainerManager;
  @Autowired
  protected TestResourceManager testResourceManager;
  @Autowired
  protected TestSearchRepository testSearchRepository;

  // Used to control and clear process/import info between test suites
  @Autowired
  protected ProcessCache processCache;
  @Autowired
  protected ImportPositionHolder importPositionHolder;
  @Autowired
  protected PartitionHolder partitionHolder;

  @Autowired
  protected BeanFactory beanFactory;

  @Autowired
  protected ObjectMapper objectMapper;

  protected ZeebeClient zeebeClient;

  protected OperateJ5Tester operateTester;

  @BeforeAll
  public void beforeAllSetup() {
    // Mocks the authentication for zeebe/search
    when(userService.getCurrentUser()).thenReturn(
        new UserDto().setUserId(DEFAULT_USER)
            .setPermissions(List.of(Permission.WRITE)));
    doReturn(TenantService.AuthenticatedTenants.allTenants()).when(tenantService).getAuthenticatedTenants();

    // Start zeebe and elasticsearch/opensearch
    zeebeContainerManager.startContainer();
    searchContainerManager.startContainer();

    zeebeClient = zeebeContainerManager.getClient();
    operateTester = beanFactory.getBean(OperateJ5Tester.class, zeebeClient);

    // Required to keep search and zeebe from hanging between test suites
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
    importPositionHolder.scheduleImportPositionUpdateTask();
    partitionHolder.setZeebeClient(zeebeClient);

    // Allows time for everything to settle and indices to show up
    zeebeStabilityDelay();

    // Implementing tests can add any additional setup needed to run once before all the tests run
    runAdditionalBeforeAllSetup();
  }

  protected void zeebeStabilityDelay() {
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  public void beforeEach() {
    // Mocks are cleared between each test, reset the authentication mocks so interactions with search don't fail
    when(userService.getCurrentUser()).thenReturn(
        new UserDto().setUserId(DEFAULT_USER)
            .setPermissions(List.of(Permission.WRITE)));
    doReturn(TenantService.AuthenticatedTenants.allTenants()).when(tenantService).getAuthenticatedTenants();

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

    // Implementing tests can add any additional teardown needed to run at the completion of the test suite
    runAdditionalAfterAllTeardown();
  }

  public void runAdditionalAfterAllTeardown() {}
}
