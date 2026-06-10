/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

class BrokerRequestTest {

  @Test
  void shouldReturnDefaultPartitionGroup() {
    // given
    final var request = new TestBrokerRequest();

    // then
    assertThat(request.getPartitionGroup()).isEqualTo(Protocol.DEFAULT_PARTITION_GROUP_NAME);
  }

  @Test
  void shouldReturnSetPartitionGroup() {
    // given
    final var request = new TestBrokerRequest();

    // when
    request.setPartitionGroup("tenant1");

    // then
    assertThat(request.getPartitionGroup()).isEqualTo("tenant1");
  }

  @Test
  void shouldRejectNullPartitionGroup() {
    // given
    final var request = new TestBrokerRequest();

    // when / then
    assertThatThrownBy(() -> request.setPartitionGroup(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("partitionGroup must not be null");
  }

  @Test
  void shouldRejectBlankPartitionGroup() {
    // given
    final var request = new TestBrokerRequest();

    // when / then
    assertThatThrownBy(() -> request.setPartitionGroup(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("partitionGroup must not be blank");
  }

  /** Minimal concrete subclass for testing. */
  private static final class TestBrokerRequest extends BrokerRequest<Void> {

    TestBrokerRequest() {
      super(0, 0);
    }

    @Override
    public void setPartitionId(final int partitionId) {}

    @Override
    public int getPartitionId() {
      return 1;
    }

    @Override
    public RequestType getRequestType() {
      return RequestType.COMMAND;
    }

    @Override
    public boolean addressesSpecificPartition() {
      return true;
    }

    @Override
    public boolean requiresPartitionId() {
      return false;
    }

    @Override
    public BufferWriter getRequestWriter() {
      return null;
    }

    @Override
    protected void setSerializedValue(final DirectBuffer buffer) {}

    @Override
    protected void wrapResponse(final DirectBuffer buffer) {}

    @Override
    protected BrokerResponse<Void> readResponse() {
      return null;
    }

    @Override
    protected Void toResponseDto(final DirectBuffer buffer) {
      return null;
    }

    @Override
    public String getType() {
      return "test";
    }

    @Override
    public int getLength() {
      return 0;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return 0;
    }
  }
}
