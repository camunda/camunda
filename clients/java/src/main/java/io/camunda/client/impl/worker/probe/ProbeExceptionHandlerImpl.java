/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.worker.probe;

import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeExceptionHandler;
import io.camunda.client.impl.Loggers;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Answers {@code ERROR} for a probe whose handler threw. Unlike the job worker, the default message
 * is not the stack trace: it ends up in a user's dialog, so it is the exception's message without
 * the values the cluster injected into the probe.
 */
public final class ProbeExceptionHandlerImpl implements ProbeExceptionHandler {

  public static final Function<ProbeExceptionHandlerContext, String>
      DEFAULT_ERROR_MESSAGE_PROVIDER =
          context -> withoutSecrets(context.getActivatedProbe(), messageOf(context.getException()));

  private static final Logger LOG = Loggers.PROBE_WORKER_LOGGER;

  private final Function<ProbeExceptionHandlerContext, String> errorMessageProvider;

  public ProbeExceptionHandlerImpl() {
    this(DEFAULT_ERROR_MESSAGE_PROVIDER);
  }

  public ProbeExceptionHandlerImpl(
      final Function<ProbeExceptionHandlerContext, String> errorMessageProvider) {
    this.errorMessageProvider =
        Objects.requireNonNull(errorMessageProvider, "errorMessageProvider must not be null");
  }

  @Override
  public ProbeAnswer handleProbeException(final ProbeExceptionHandlerContext context) {
    final ActivatedProbe probe = context.getActivatedProbe();
    // the stack trace carries the exception's messages, so it is logged without the secrets too
    LOG.warn(
        "Worker {} failed to handle probe with key {} of probe ID {}, answering ERROR: {}",
        probe.getWorker(),
        probe.getKey(),
        probe.getProbeId(),
        withoutSecrets(probe, stackTraceOf(context.getException())));
    return ProbeAnswer.error(errorMessage(context));
  }

  private String errorMessage(final ProbeExceptionHandlerContext context) {
    try {
      final String message = errorMessageProvider.apply(context);
      if (message != null) {
        return message;
      }
      LOG.warn("The errorMessageProvider returned no message, using the default message");
    } catch (final Exception e) {
      LOG.warn("The errorMessageProvider failed, using the default message", e);
    }
    return DEFAULT_ERROR_MESSAGE_PROVIDER.apply(context);
  }

  private static String messageOf(final Exception exception) {
    final String message = exception.getMessage();
    return message == null ? exception.getClass().getName() : message;
  }

  private static String withoutSecrets(final ActivatedProbe probe, final String text) {
    return probe instanceof ActivatedProbeImpl
        ? ((ActivatedProbeImpl) probe).withoutSecrets(text)
        : text;
  }

  private static String stackTraceOf(final Exception exception) {
    final StringWriter stackTrace = new StringWriter();
    exception.printStackTrace(new PrintWriter(stackTrace));
    return stackTrace.toString();
  }
}
