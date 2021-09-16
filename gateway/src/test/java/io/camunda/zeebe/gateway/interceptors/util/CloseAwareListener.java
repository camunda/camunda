package io.camunda.zeebe.gateway.interceptors.util;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.ServerCall.Listener;

/**
 * Utility interceptor which stops forwarding any callbacks to its delegate when closed in order to
 * prevent errors.
 */
class CloseAwareListener<ReqT> extends SimpleForwardingServerCallListener<ReqT> {
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
