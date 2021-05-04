/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.zeebe.PartitionHolder;
import io.camunda.tasklist.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.test.ClientRule;
import io.camunda.zeebe.test.EmbeddedBrokerRule;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public abstract class TasklistZeebeIntegrationTest extends TasklistIntegrationTest {

  public static final String USERNAME_DEFAULT = "demo";
  @Autowired public BeanFactory beanFactory;
  @Rule public final TasklistZeebeRule zeebeRule;
  public EmbeddedBrokerRule brokerRule;
  @Rule public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @MockBean protected ZeebeClient mockedZeebeClient;
  // we don't want to create ZeebeClient, we will rather use the one from
  // test rule
  protected ZeebeClient zeebeClient;
  protected ClientRule clientRule;
  @Autowired protected PartitionHolder partitionHolder;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected TasklistProperties tasklistProperties;
  protected TasklistTester tester;
  @MockBean protected UserReader userReader;
  @Autowired private ProcessCache processCache;
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;

  public TasklistZeebeIntegrationTest() {
    zeebeRule = new TasklistZeebeRule();
  }

  @Before
  public void before() {
    super.before();

    clientRule = zeebeRule.getClientRule();
    assertThat(clientRule).as("clientRule is not null").isNotNull();
    brokerRule = zeebeRule.getBrokerRule();
    assertThat(brokerRule).as("brokerRule is not null").isNotNull();

    zeebeClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(TasklistTester.class, zeebeClient, elasticsearchTestRule);

    processCache.clearCache();
    importPositionHolder.clearCache();
    partitionHolder.setZeebeClient(getClient());

    setDefaultCurrentUser();
  }

  protected void setDefaultCurrentUser() {
    setCurrentUser(getDefaultCurrentUser());
  }

  protected UserDTO getDefaultCurrentUser() {
    return new UserDTO().setUsername(USERNAME_DEFAULT).setFirstname("Demo").setLastname("User");
  }

  protected void setCurrentUser(UserDTO user) {
    Mockito.when(userReader.getCurrentUser()).thenReturn(user);
    Mockito.when(userReader.getUsersByUsernames(any())).thenReturn(List.of(user));
  }

  @After
  public void after() {
    setDefaultCurrentUser();
    processCache.clearCache();
    importPositionHolder.clearCache();
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  public String getWorkerName() {
    return workerName;
  }

  protected void clearMetrics() {
    for (Meter meter : meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }
}
