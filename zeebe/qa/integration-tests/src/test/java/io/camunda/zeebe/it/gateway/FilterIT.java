/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class FilterIT {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .withGatewayConfig(
              (memberId, testGateway) -> {
                final var interceptorCfg = new InterceptorCfg();
                interceptorCfg.setId("test");
                interceptorCfg.setClassName(CustomFilter.class.getName());
                testGateway.gatewayConfig().setInterceptors(List.of(interceptorCfg));
              })
          .build();

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void initClientAndInstances() {
    client = cluster.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    final ZeebeResourcesHelper resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldUpdateUserTaskWithAction() {
    // when
    client.newTopologyRequest().send().join();

    // then
    //    ZeebeAssertHelper.assertUserTaskUpdated(
    //        userTaskKey, (userTask) -> assertThat(userTask.getAction()).isEqualTo("foo"));
  }

  //  @Test
  //  void shouldReturnRejectionWithCorrectTypeAndReason() throws InterruptedException {
  //    // given
  //    final var gateway = cluster.availableGateway();
  //    final var latch = new CountDownLatch(1);
  //    final AtomicReference<Throwable> errorResponse = new AtomicReference<>();
  //    final var client = gateway.bean(BrokerClient.class);
  //
  //    // when
  //    client.sendRequestWithRetry(
  //        new BrokerCreateProcessInstanceRequest(),
  //        (k, r) -> {},
  //        error -> {
  //          errorResponse.set(error);
  //          latch.countDown();
  //        });
  //
  //    // then
  //    latch.await();
  //    final var error = errorResponse.get();
  //    assertThat(error).isInstanceOf(BrokerRejectionException.class);
  //    final BrokerRejection rejection = ((BrokerRejectionException) error).getRejection();
  //    assertThat(rejection.type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  //    assertThat(rejection.reason())
  //        .isEqualTo("Expected at least a bpmnProcessId or a key greater than -1, but none
  // given");
  //  }

  public static class CustomFilter implements Filter {

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
      Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(
        final ServletRequest servletRequest,
        final ServletResponse servletResponse,
        final FilterChain filterChain)
        throws IOException, ServletException {

      throw new RuntimeException("I'm FILTERING!!!!");
    }

    @Override
    public void destroy() {
      Filter.super.destroy();
    }
  }
}
