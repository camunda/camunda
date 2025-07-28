/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.TenantAccess;
import java.util.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base definition for a test that requires opensearch/elasticsearch but not zeebe. The test suite
 * automatically starts search before all the tests run, and then tears it down once all the tests
 * have finished.
 */
@SpringBootTest(
    classes = {
      TestApplication.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class,
    },
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      "camunda.security.authorizations.enabled=false"
    })
@WebAppConfiguration
@WithMockUser(DEFAULT_USER)
@TestInstance(
    TestInstance.Lifecycle
        .PER_CLASS) // Lifecycle required to use BeforeAll and AfterAll in non-static fashion
public class OperateSearchAbstractIT {
  public static final String DEFAULT_USER = "testuser";
  // These are mocked so we can bypass authentication issues when connecting to search
  @MockBean protected CamundaAuthenticationProvider camundaAuthenticationProvider;
  @MockBean protected TenantService tenantService;
  @Autowired protected ProcessCache processCache;
  @Autowired protected TestSearchRepository testSearchRepository;
  @Autowired protected SearchContainerManager searchContainerManager;
  @Autowired protected TestResourceManager testResourceManager;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  @BeforeAll
  public void beforeAllSetup() throws Exception {
    // Mocks the authentication for search
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

    // Start elasticsearch/opensearch
    searchContainerManager.startContainer();

    // Required to keep search from hanging between test suites
    processCache.clearCache();

    // Implementing tests can add any additional setup needed to run once before all the tests run
    runAdditionalBeforeAllSetup();
  }

  @BeforeEach
  public void beforeEach() throws Exception {
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

    // Implementing tests can add any additional setup needed to run before each test
    runAdditionalBeforeEachSetup();
  }

  protected void runAdditionalBeforeAllSetup() throws Exception {}

  protected void runAdditionalBeforeEachSetup() throws Exception {}

  @AfterAll
  public void afterAllTeardown() {
    // Stop search once all the test are finished
    searchContainerManager.stopContainer();

    // Required to keep search from hanging between test suites
    processCache.clearCache();

    // Implementing tests can add any additional teardown needed to run at the completion of the
    // test suite
    runAdditionalAfterAllTeardown();
  }

  public void runAdditionalAfterAllTeardown() {}

  protected void assertErrorMessageContains(final MvcResult mvcResult, final String text) {
    assertThat(mvcResult.getResolvedException().getMessage()).contains(text);
  }
}
