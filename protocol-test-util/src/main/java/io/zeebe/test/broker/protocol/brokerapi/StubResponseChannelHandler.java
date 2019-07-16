/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class StubResponseChannelHandler implements ServerRequestHandler {

  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final List<ResponseStub<ExecuteCommandRequest>> cmdRequestStubs = new ArrayList<>();
  protected final MsgPackHelper msgPackHelper;

  // can also be used for verification
  protected final List<Object> allRequests = new CopyOnWriteArrayList<>();
  protected final List<ExecuteCommandRequest> commandRequests = new CopyOnWriteArrayList<>();

  protected ServerResponse response = new ServerResponse();

  public StubResponseChannelHandler(MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  public void addExecuteCommandRequestStub(ResponseStub<ExecuteCommandRequest> stub) {
    cmdRequestStubs.add(0, stub); // add to front such that more recent stubs override older ones
  }

  public List<ExecuteCommandRequest> getReceivedCommandRequests() {
    return commandRequests;
  }

  public List<Object> getAllReceivedRequests() {
    return allRequests;
  }

  @Override
  public boolean onRequest(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length,
      long requestId) {
    final MutableDirectBuffer copy = new UnsafeBuffer(new byte[length]);
    copy.putBytes(0, buffer, offset, length);

    headerDecoder.wrap(copy, 0);

    boolean requestHandled = false;
    if (ExecuteCommandRequestDecoder.TEMPLATE_ID == headerDecoder.templateId()) {
      final ExecuteCommandRequest request = new ExecuteCommandRequest(remoteAddress, msgPackHelper);

      request.wrap(copy, 0, length);
      commandRequests.add(request);
      allRequests.add(request);

      requestHandled = handleRequest(output, request, cmdRequestStubs, remoteAddress, requestId);
    }

    if (!requestHandled) {
      throw new RuntimeException(
          String.format(
              "no stub applies to request with schema id %s and template id %s ",
              headerDecoder.schemaId(), headerDecoder.templateId()));
    } else {
      return true;
    }
  }

  protected <T> boolean handleRequest(
      ServerOutput output,
      T request,
      List<? extends ResponseStub<T>> responseStubs,
      RemoteAddress requestSource,
      long requestId) {
    for (ResponseStub<T> stub : responseStubs) {
      if (stub.applies(request)) {
        if (stub.shouldRespond()) {
          final MessageBuilder<T> responseWriter = stub.getResponseWriter();
          responseWriter.initializeFrom(request);

          response.reset().remoteAddress(requestSource).requestId(requestId).writer(responseWriter);

          responseWriter.beforeResponse();

          return output.sendResponse(response);
        } else {
          // just ignore the request; this can be used to simulate requests that never return
          return true;
        }
      }
    }
    return false;
  }
}
