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
package io.zeebe.model.bpmn.impl.transformation.nodes.task;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.model.bpmn.impl.metadata.TaskHeaderImpl;
import io.zeebe.model.bpmn.impl.metadata.TaskHeadersImpl;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TaskHeadersTransformer {
  private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();

  public void transform(TaskHeadersImpl taskHeaders) {
    final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    final List<TaskHeaderImpl> headers = taskHeaders.getTaskHeaders();

    if (!headers.isEmpty()) {
      final ExpandableArrayBuffer expandableBuffer =
          new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * headers.size());
      msgPackWriter.wrap(expandableBuffer, 0);
      msgPackWriter.writeMapHeader(headers.size());

      for (int h = 0; h < headers.size(); h++) {
        final TaskHeaderImpl header = headers.get(h);

        final DirectBuffer key = wrapString(header.getKey());
        msgPackWriter.writeString(key);

        final DirectBuffer value = wrapString(header.getValue());
        msgPackWriter.writeString(value);
      }

      buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());
    }

    taskHeaders.setEncodedMsgpack(buffer);
  }
}
