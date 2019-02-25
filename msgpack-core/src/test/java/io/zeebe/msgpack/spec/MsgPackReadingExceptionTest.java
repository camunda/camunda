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

import java.util.Arrays;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
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
public class MsgPackReadingExceptionTest {

  protected static final DirectBuffer NEVER_USED_BUF = new UnsafeBuffer(new byte[] {(byte) 0xc1});
  protected static final String NEGATIVE_BUF_SIZE_EXCEPTION_MSG = "Negative buffer size";

  @Rule public ExpectedException exception = ExpectedException.none();

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    final String template = "Unable to determine %s type, found unknown header byte 0xc1";
    return Arrays.asList(
        new Object[][] {
          {String.format(template, "long"), codeUnderTest(MsgPackReader::readInteger)},
          {String.format(template, "array"), codeUnderTest(MsgPackReader::readArrayHeader)},
          {String.format(template, "binary"), codeUnderTest(MsgPackReader::readBinaryLength)},
          {String.format(template, "boolean"), codeUnderTest(MsgPackReader::readBoolean)},
          {String.format(template, "float"), codeUnderTest(MsgPackReader::readFloat)},
          {String.format(template, "map"), codeUnderTest(MsgPackReader::readMapHeader)},
          {String.format(template, "string"), codeUnderTest(MsgPackReader::readStringLength)},
          {"Unknown token format 'NEVER_USED'", codeUnderTest(MsgPackReader::readToken)}
        });
  }

  @Parameter(0)
  public String expectedExceptionMessage;

  @Parameter(1)
  public Consumer<MsgPackReader> codeUnderTest;

  protected MsgPackReader reader;

  @Before
  public void setUp() {
    reader = new MsgPackReader();
  }

  @Test
  public void shouldNotReadInvalidSequence() {
    // given
    reader.wrap(NEVER_USED_BUF, 0, NEVER_USED_BUF.capacity());

    // then
    exception.expect(MsgpackReaderException.class);
    exception.expectMessage(expectedExceptionMessage);

    // when
    codeUnderTest.accept(reader);
  }

  protected static Consumer<MsgPackReader> codeUnderTest(Consumer<MsgPackReader> arg) {
    return arg;
  }
}
