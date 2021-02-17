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
package io.zeebe.journal.file.record;

import io.zeebe.journal.JournalRecord;
import java.nio.ByteBuffer;

public interface JournalRecordBufferReader {

  /**
   * Reads the {@link JournalRecord} from the buffer at it's current position ({@code *
   * buffer.position()}). A valid record must exist in the buffer at this position.
   *
   * @param buffer to read
   * @return a journal record that is read.
   */
  JournalRecord read(ByteBuffer buffer);
}
