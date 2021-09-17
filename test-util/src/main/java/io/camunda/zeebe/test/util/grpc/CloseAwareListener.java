/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.grpc;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.ServerCall.Listener;

/**
 * Utility interceptor which stops forwarding any callbacks to its delegate when closed in order to
 * prevent errors.
 */
public class CloseAwareListener<ReqT> extends SimpleForwardingServerCallListener<ReqT> {
  protected volatile boolean isClosed;

  public CloseAwareListener(final Listener<ReqT> delegate) {
    super(delegate);
  }

  @Override
  public void onMessage(final ReqT message) {
    if (!isClosed) {
      super.onMessage(message);
    }
  }

  @Override
  public void onHalfClose() {
    if (!isClosed) {
      super.onHalfClose();
    }
  }

  @Override
  public void onCancel() {
    if (!isClosed) {
      super.onCancel();
    }
  }

  @Override
  public void onComplete() {
    if (!isClosed) {
      super.onComplete();
    }
  }

  @Override
  public void onReady() {
    if (!isClosed) {
      super.onReady();
    }
  }
}
