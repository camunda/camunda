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
package io.zeebe.model.bpmn.impl.error;

import io.zeebe.model.bpmn.impl.Result;
import io.zeebe.model.bpmn.impl.instance.BaseElement;
import java.util.ArrayList;
import java.util.List;

public class ErrorCollector implements Result {
  private static final String MESSAGE = "[line:%s] (%s) %s";

  private final List<Entry> errors = new ArrayList<>();

  public void addError(BaseElement element, String message) {
    errors.add(new Entry(message, element));
  }

  @Override
  public boolean success() {
    return !hasErrors();
  }

  @Override
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  @Override
  public String format() {
    final StringBuilder builder = new StringBuilder();

    formatErrors(builder);
    return builder.toString();
  }

  private void formatErrors(StringBuilder builder) {
    for (Entry error : errors) {
      builder.append(
          String.format(
              MESSAGE, getLine(error.element), getElementName(error.element), error.message));
      builder.append("\n");
    }
  }

  @Override
  public String formatErrors() {
    final StringBuilder builder = new StringBuilder();
    formatErrors(builder);
    return builder.toString();
  }

  private String getElementName(BaseElement element) {
    String name = "unknown";
    final String elementName = element.getElementName();
    final String namespace = element.getNamespace();
    if (elementName != null) {
      name = namespace + ":" + elementName;
    }
    return name;
  }

  private String getLine(BaseElement element) {
    String line = "unknown";
    final Integer lineNumber = element.getLineNumber();
    if (lineNumber != null) {
      line = String.valueOf(lineNumber);
    }
    return line;
  }

  private class Entry {
    private String message;
    private BaseElement element;

    Entry(String message, BaseElement element) {
      this.message = message;
      this.element = element == null ? new BaseElement() : element;
    }
  }

  @Override
  public String toString() {
    return format();
  }
}
