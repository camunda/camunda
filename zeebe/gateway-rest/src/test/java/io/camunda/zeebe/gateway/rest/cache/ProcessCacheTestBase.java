/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;

import io.camunda.zeebe.gateway.rest.cache.ProcessCacheTestBase.TestConfig;
import io.camunda.zeebe.gateway.rest.cache.ProcessCacheTestBase.TestConfig.GatewayRestProperties;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.XmlUtil;
import io.camunda.zeebe.gateway.rest.util.XmlUtil.ProcessFlowNode;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Arrays;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ProcessCache.class, TestConfig.class})
public class ProcessCacheTestBase {

  @Autowired protected ProcessCache processCache;
  @Autowired protected GatewayRestConfiguration configuration;
  @MockBean protected XmlUtil xmlUtil;

  @BeforeEach
  public void setUp() {
    mockLoad(Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")));
  }

  @AfterEach
  public void tearDown() {
    processCache.getCache().cleanUp();
    processCache.getCache().invalidateAll();
    processCache.getCache().cleanUp();
  }

  private <T> Answer<T> mockLoadAnswer(final Tuple<Long, ProcessFlowNode>... nodes) {
    return invocation -> {
      final var consumer = invocation.<BiConsumer<Long, ProcessFlowNode>>getArgument(1);
      Arrays.stream(nodes).forEach(t -> consumer.accept(t.getLeft(), t.getRight()));
      return null;
    };
  }

  @SafeVarargs
  protected final void mockLoad(final Tuple<Long, ProcessFlowNode>... nodes) {
    doAnswer(mockLoadAnswer(nodes)).when(xmlUtil).extractFlowNodeNames(anyLong(), any());
  }

  @SafeVarargs
  protected final void mockLoadAll(final Tuple<Long, ProcessFlowNode>... nodes) {
    doAnswer(mockLoadAnswer(nodes)).when(xmlUtil).extractFlowNodeNames(anySet(), any());
  }

  @TestConfiguration
  @EnableConfigurationProperties({GatewayRestProperties.class})
  public static class TestConfig {

    @ConfigurationProperties("camunda.rest")
    public static final class GatewayRestProperties extends GatewayRestConfiguration {}
  }
}
