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
package io.zeebe.msgpack.spec;

import io.zeebe.msgpack.spec.MsgPackUtil.CheckedConsumer;
import java.util.Arrays;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MsgPackWritingExceptionTest {

  protected static final int BUFFER_CAPACITY = 1024;
  protected static final int WRITE_OFFSET = 123;
  protected MutableDirectBuffer actualValueBuffer = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
  protected static final String NEGATIVE_BUF_SIZE_EXCEPTION_MSG =
      "Negative value should not be accepted by size value and unsigned 64bit integer";

  @Rule public ExpectedException exception = ExpectedException.none();

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {NEGATIVE_BUF_SIZE_EXCEPTION_MSG, codeUnderTest((r) -> r.writeArrayHeader(-1))},
          {NEGATIVE_BUF_SIZE_EXCEPTION_MSG, codeUnderTest((r) -> r.writeBinaryHeader(-1))},
          {NEGATIVE_BUF_SIZE_EXCEPTION_MSG, codeUnderTest((r) -> r.writeMapHeader(-1))},
          {NEGATIVE_BUF_SIZE_EXCEPTION_MSG, codeUnderTest((r) -> r.writeStringHeader(-1))}
        });
  }

  @Parameter(0)
  public String expectedExceptionMessage;

  @Parameter(1)
  public CheckedConsumer<MsgPackWriter> codeUnderTest;

  @Before
  public void setUp() {
    Arrays.fill(actualValueBuffer.byteArray(), (byte) 0);
  }

  @Test
  public void shouldNotReadNegativeSize() throws Exception {
    // given
    final MsgPackWriter writer = new MsgPackWriter();
    writer.wrap(actualValueBuffer, WRITE_OFFSET);

    // then
    exception.expect(MsgpackWriterException.class);
    exception.expectMessage(expectedExceptionMessage);

    // when
    codeUnderTest.accept(writer);
  }

  protected static CheckedConsumer<MsgPackWriter> codeUnderTest(
      CheckedConsumer<MsgPackWriter> arg) {
    return arg;
  }
}
