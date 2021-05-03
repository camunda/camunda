/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.RequestHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.impl.ServerResponseImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class StubRequestHandler implements RequestHandler {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final List<ResponseStub<ExecuteCommandRequest>> cmdRequestStubs = new ArrayList<>();
  private final MsgPackHelper msgPackHelper;

  // can also be used for verification
  private final List<Object> allRequests = new CopyOnWriteArrayList<>();
  private final List<ExecuteCommandRequest> commandRequests = new CopyOnWriteArrayList<>();

  private final ServerResponseImpl response = new ServerResponseImpl();

  StubRequestHandler(final MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  void addExecuteCommandRequestStub(final ResponseStub<ExecuteCommandRequest> stub) {
    cmdRequestStubs.add(0, stub); // add to front such that more recent stubs override older ones
  }

  List<ExecuteCommandRequest> getReceivedCommandRequests() {
    return commandRequests;
  }

  @Override
  public void onRequest(
      final ServerOutput output,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    final MutableDirectBuffer copy = new UnsafeBuffer(new byte[length]);
    copy.putBytes(0, buffer, offset, length);

    headerDecoder.wrap(copy, 0);

    boolean requestHandled = false;
    if (ExecuteCommandRequestDecoder.TEMPLATE_ID == headerDecoder.templateId()) {
      final ExecuteCommandRequest request = new ExecuteCommandRequest(msgPackHelper);

      request.wrap(copy, 0, length);
      commandRequests.add(request);
      allRequests.add(request);

      requestHandled = handleRequest(output, partitionId, request, cmdRequestStubs, requestId);
    }

    if (!requestHandled) {
      throw new RuntimeException(
          String.format(
              "no stub applies to request with schema id %s and template id %s ",
              headerDecoder.schemaId(), headerDecoder.templateId()));
    }
  }

  private <T> boolean handleRequest(
      final ServerOutput output,
      final int partitionId,
      final T request,
      final List<? extends ResponseStub<T>> responseStubs,
      final long requestId) {
    for (final ResponseStub<T> stub : responseStubs) {
      if (stub.applies(request)) {
        if (stub.shouldRespond()) {
          final MessageBuilder<T> responseWriter = stub.getResponseWriter();
          responseWriter.initializeFrom(request);

          response
              .reset()
              .setRequestId(requestId)
              .setPartitionId(partitionId)
              .writer(responseWriter);

          responseWriter.beforeResponse();

          output.sendResponse(response);
        }
        return true;
      }
    }
    return false;
  }
}
