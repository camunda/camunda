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
package io.zeebe.msgpack.jsonpath;

import io.zeebe.msgpack.filter.MsgPackFilter;
import io.zeebe.msgpack.query.MsgPackFilterContext;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JsonPathQuery {
  protected static final int MAX_DEPTH = 30;
  protected static final int MAX_FILTER_CONTEXT_LENGTH = 50;
  protected static final int NO_INVALID_POSITION = -1;

  protected MsgPackFilter[] filters;
  protected MsgPackFilterContext filterInstances =
      new MsgPackFilterContext(MAX_DEPTH, MAX_FILTER_CONTEXT_LENGTH);

  protected UnsafeBuffer expressionBuffer = new UnsafeBuffer(0, 0);
  protected DirectBuffer variableName = new UnsafeBuffer(0, 0);

  protected int invalidPosition;
  protected String errorMessage;

  public JsonPathQuery(MsgPackFilter[] filters) {
    this.filters = filters;
  }

  public void wrap(DirectBuffer buffer, int offset, int length) {
    filterInstances.clear();
    invalidPosition = NO_INVALID_POSITION;

    expressionBuffer.wrap(buffer, offset, length);
  }

  public MsgPackFilterContext getFilterInstances() {
    return filterInstances;
  }

  public MsgPackFilter[] getFilters() {
    return filters;
  }

  public void invalidate(int position, String message) {
    this.invalidPosition = position;
    this.errorMessage = message;
  }

  public boolean isValid() {
    return invalidPosition == NO_INVALID_POSITION;
  }

  public int getInvalidPosition() {
    return invalidPosition;
  }

  public String getErrorReason() {
    return errorMessage;
  }

  public DirectBuffer getExpression() {
    return expressionBuffer;
  }

  public DirectBuffer getVariableName() {
    return variableName;
  }

  public void setVariableName(byte[] topLevelVariable) {
    this.variableName.wrap(topLevelVariable);
  }
}
