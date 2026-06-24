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
package io.camunda.process.test.impl.runtime.logging;

import java.util.Optional;

public class CamundaLogEntry implements LogEntry {

  private String severity;
  private String message;
  private CamundaLogContext context;

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getLoggerName() {
    return Optional.ofNullable(context).map(CamundaLogContext::getLoggerName).orElse("?");
  }

  @Override
  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public void setSeverity(final String severity) {
    this.severity = severity;
  }

  public CamundaLogContext getContext() {
    return context;
  }

  public void setContext(final CamundaLogContext context) {
    this.context = context;
  }
}
