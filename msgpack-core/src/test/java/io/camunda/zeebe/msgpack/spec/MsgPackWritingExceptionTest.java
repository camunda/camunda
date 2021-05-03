/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
public final class MsgPackWritingExceptionTest {

  protected static final int BUFFER_CAPACITY = 1024;
  protected static final int WRITE_OFFSET = 123;
  protected static final String NEGATIVE_BUF_SIZE_EXCEPTION_MSG =
      "Negative value should not be accepted by size value and unsigned 64bit integer";
  @Rule public final ExpectedException exception = ExpectedException.none();

  @Parameter(0)
  public String expectedExceptionMessage;

  @Parameter(1)
  public CheckedConsumer<MsgPackWriter> codeUnderTest;

  protected final MutableDirectBuffer actualValueBuffer =
      new UnsafeBuffer(new byte[BUFFER_CAPACITY]);

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
      final CheckedConsumer<MsgPackWriter> arg) {
    return arg;
  }
}
