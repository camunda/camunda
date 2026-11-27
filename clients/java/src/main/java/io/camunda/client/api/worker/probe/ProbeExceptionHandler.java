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
package io.camunda.client.api.worker.probe;

import io.camunda.client.impl.worker.probe.ProbeExceptionHandlerImpl;
import java.util.function.Function;

/**
 * Decides the answer to a probe whose {@link ProbeHandler} threw (alpha). The worker sends the
 * returned answer.
 */
@FunctionalInterface
public interface ProbeExceptionHandler {

  ProbeAnswer handleProbeException(ProbeExceptionHandlerContext context);

  /**
   * @return the default handler: it answers {@code ERROR} with the exception's message, without the
   *     values the cluster injected into this probe, cut to {@value ProbeAnswer#MAX_MESSAGE_LENGTH}
   *     characters
   */
  static ProbeExceptionHandler createDefault() {
    return new ProbeExceptionHandlerImpl();
  }

  /**
   * @param errorMessageProvider provides the message of the {@code ERROR} answer; it ends up in
   *     front of a user, so it must not hold secret values
   * @return a handler that answers {@code ERROR} with the provided message
   */
  static ProbeExceptionHandler withErrorMessageProvider(
      final Function<ProbeExceptionHandlerContext, String> errorMessageProvider) {
    return new ProbeExceptionHandlerImpl(errorMessageProvider);
  }

  final class ProbeExceptionHandlerContext {
    private final ActivatedProbe activatedProbe;
    private final Exception exception;

    public ProbeExceptionHandlerContext(
        final ActivatedProbe activatedProbe, final Exception exception) {
      this.activatedProbe = activatedProbe;
      this.exception = exception;
    }

    public ActivatedProbe getActivatedProbe() {
      return activatedProbe;
    }

    public Exception getException() {
      return exception;
    }
  }
}
