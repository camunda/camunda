/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack.spec;

import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.ARRAY32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FIXSTR_PREFIX;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.MAP32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT64;

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
public final class MsgPackReadingBoundaryCheckingExceptionTest {

  private static final String NEGATIVE_BUF_SIZE_EXCEPTION_MSG =
      "Negative value should not be accepted by size value and unsigned 64bit integer";

  @Rule public final ExpectedException exception = ExpectedException.none();

  @Parameter(0)
  public byte[] testingBuf;

  @Parameter(1)
  public Consumer<MsgPackReader> codeUnderTest;

  @Parameter(2)
  public String exceptionMessage;

  protected MsgPackReader reader;

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            new byte[] {(byte) ARRAY32, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
            codeUnderTest((r) -> r.readArrayHeader()),
            NEGATIVE_BUF_SIZE_EXCEPTION_MSG
          },
          {
            new byte[] {(byte) BIN32, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
            codeUnderTest((r) -> r.readBinaryLength()),
            NEGATIVE_BUF_SIZE_EXCEPTION_MSG
          },
          {
            new byte[] {(byte) MAP32, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
            codeUnderTest((r) -> r.readMapHeader()),
            NEGATIVE_BUF_SIZE_EXCEPTION_MSG
          },
          {
            new byte[] {(byte) STR32, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
            codeUnderTest((r) -> r.readStringLength()),
            NEGATIVE_BUF_SIZE_EXCEPTION_MSG
          },
          {
            new byte[] {
              (byte) UINT64,
              (byte) 0xff,
              (byte) 0xff,
              (byte) 0xff,
              (byte) 0xff,
              (byte) 0xff,
              (byte) 0xff,
              (byte) 0xff,
              (byte) 0xff
            },
            codeUnderTest((r) -> r.readInteger()),
            NEGATIVE_BUF_SIZE_EXCEPTION_MSG
          },
          {
            new byte[] {(byte) FIXSTR_PREFIX | (byte) 0x01},
            codeUnderTest((r) -> r.readToken()),
            "Reading 1 bytes past buffer capacity(1) in range [1:2]"
          },
        });
  }

  @Before
  public void setUp() {
    reader = new MsgPackReader();
  }

  @Test
  public void shouldNotReadNegativeValue() {
    // given
    final DirectBuffer negativeTestingBuf = new UnsafeBuffer(testingBuf);
    reader.wrap(negativeTestingBuf, 0, negativeTestingBuf.capacity());

    // then
    exception.expect(MsgpackReaderException.class);
    exception.expectMessage(exceptionMessage);

    // when
    codeUnderTest.accept(reader);
  }

  protected static Consumer<MsgPackReader> codeUnderTest(final Consumer<MsgPackReader> arg) {
    return arg;
  }
}
