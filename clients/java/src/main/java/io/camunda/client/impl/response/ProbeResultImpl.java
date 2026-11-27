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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.ProbeOutcome;
import io.camunda.client.api.response.ProbeResult;
import io.camunda.client.protocol.rest.ProbeErrorResult;
import io.camunda.client.protocol.rest.ProbeOkResult;
import io.camunda.client.protocol.rest.ProbeUnsupportedResult;
import java.util.Map;

public final class ProbeResultImpl implements ProbeResult {

  private final ProbeOutcome outcome;
  private final String worker;
  private final Map<String, Object> data;
  private final String message;

  public ProbeResultImpl(final io.camunda.client.protocol.rest.ProbeResult result) {
    if (result instanceof ProbeOkResult) {
      final ProbeOkResult ok = (ProbeOkResult) result;
      outcome = ProbeOutcome.OK;
      worker = ok.getWorker();
      data = ok.getData();
      message = null;
    } else if (result instanceof ProbeErrorResult) {
      final ProbeErrorResult error = (ProbeErrorResult) result;
      outcome = ProbeOutcome.ERROR;
      worker = error.getWorker();
      data = null;
      message = error.getMessage();
    } else if (result instanceof ProbeUnsupportedResult) {
      outcome = ProbeOutcome.UNSUPPORTED;
      worker = ((ProbeUnsupportedResult) result).getWorker();
      data = null;
      message = null;
    } else {
      throw new IllegalArgumentException("Expected a probe result, but got: " + result);
    }
  }

  @Override
  public ProbeOutcome getOutcome() {
    return outcome;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ProbeResult{outcome="
        + outcome
        + ", worker="
        + worker
        + ", data="
        + data
        + ", message="
        + message
        + "}";
  }
}
