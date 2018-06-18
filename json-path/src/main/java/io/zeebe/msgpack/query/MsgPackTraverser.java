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
package io.zeebe.msgpack.query;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackToken;
import org.agrona.DirectBuffer;

public class MsgPackTraverser {

  protected static final int NO_INVALID_POSITION = -1;

  protected String errorMessage;
  protected int invalidPosition;

  protected MsgPackReader msgPackReader = new MsgPackReader();

  public void wrap(DirectBuffer buffer, int offset, int length) {
    this.msgPackReader.wrap(buffer, offset, length);
    this.invalidPosition = NO_INVALID_POSITION;
    this.errorMessage = null;
  }

  public void reset() {
    msgPackReader.reset();
    this.invalidPosition = NO_INVALID_POSITION;
    this.errorMessage = null;
  }

  /**
   * @param visitor
   * @return true if document could be traversed successfully
   */
  public boolean traverse(MsgPackTokenVisitor visitor) {
    while (msgPackReader.hasNext()) {
      final int nextTokenPosition = msgPackReader.getOffset();

      final MsgPackToken nextToken;
      try {
        nextToken = msgPackReader.readToken();
      } catch (Exception e) {
        errorMessage = e.getMessage();
        invalidPosition = nextTokenPosition;
        return false;
      }

      visitor.visitElement(nextTokenPosition, nextToken);
    }

    return true;
  }

  public int getInvalidPosition() {
    return invalidPosition;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
