/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.cmd.BrokerErrorException;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerError;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerErrorResponse;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.gateway.query.impl.QueryApiImpl;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ErrorCode;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class QueryApiTest extends GatewayTest {
  @Parameter(0)
  public String name;

  @Parameter(1)
  public Querier querier;

  @Parameters(name = "{index}: {0}")
  public static Object[][] queries() {
    return new Object[][] {
      new Object[] {"getBpmnProcessIdForProcess", (Querier) QueryApi::getBpmnProcessIdFromProcess},
      new Object[] {
        "getBpmnProcessIdForProcessInstance",
        (Querier) QueryApi::getBpmnProcessIdFromProcessInstance
      },
      new Object[] {"getBpmnProcessIdForProcessJob", (Querier) QueryApi::getBpmnProcessIdFromJob},
    };
  }

  @Test
  public void shouldGetBpmnProcessId() {
    // given
    final var key = Protocol.encodePartitionId(1, 1);
    final var api = new QueryApiImpl(brokerClient);
    final var timeout = Duration.ofSeconds(5);
    final var stub = new QueryStub(new BrokerResponse<>("myProcess", 1, 1));
    stub.registerWith(brokerClient);

    // when
    final var result = querier.query(api, key, timeout);

    // then
    assertThat(result).succeedsWithin(timeout).isEqualTo("myProcess");
  }

  @Test
  public void shouldCompleteExceptionallyOnError() {
    // given
    final var key = Protocol.encodePartitionId(1, 1);
    final var api = new QueryApiImpl(brokerClient);
    final var timeout = Duration.ofSeconds(5);
    final var stub =
        new QueryStub(
            new BrokerErrorResponse<>(
                new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "Leader mismatch")));
    stub.registerWith(brokerClient);

    // when
    final var result = querier.query(api, key, timeout);

    // then
    assertThat(result)
        .failsWithin(timeout)
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(BrokerErrorException.class);
  }

  private interface Querier {
    CompletionStage<String> query(final QueryApi api, final long key, final Duration timeout);
  }
}
