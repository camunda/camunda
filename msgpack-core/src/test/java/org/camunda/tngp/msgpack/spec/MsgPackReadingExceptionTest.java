package org.camunda.tngp.msgpack.spec;

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
public class MsgPackReadingExceptionTest
{

    protected static final DirectBuffer NEVER_USED_BUF = new UnsafeBuffer(new byte[]{ (byte) 0xc1 });

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
            {
                "Not a long",
                codeUnderTest((r) -> r.readInteger())
            },
            {
                "Not an array",
                codeUnderTest((r) -> r.readArrayHeader())
            },
            {
                "Not binary",
                codeUnderTest((r) -> r.readBinaryLength())
            },
            {
                "Not a boolean",
                codeUnderTest((r) -> r.readBoolean())
            },
            {
                "Not a float",
                codeUnderTest((r) -> r.readFloat())
            },
            {
                "Not a map",
                codeUnderTest((r) -> r.readMapHeader())
            },
            {
                "Not a string",
                codeUnderTest((r) -> r.readStringLength())
            },
            {
                "Unsupported token format",
                codeUnderTest((r) -> r.readToken())
            }
        });
    }

    @Parameter(0)
    public String expectedExceptionMessage;

    @Parameter(1)
    public Consumer<MsgPackReader> codeUnderTest;

    protected MsgPackReader reader;

    @Before
    public void setUp()
    {
        reader = new MsgPackReader();
    }

    @Test
    public void shouldNotReadInvalidSequence()
    {
        // given
        reader.wrap(NEVER_USED_BUF, 0, NEVER_USED_BUF.capacity());

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage(expectedExceptionMessage);

        // when
        codeUnderTest.accept(reader);
    }

    protected static Consumer<MsgPackReader> codeUnderTest(Consumer<MsgPackReader> arg)
    {
        return arg;
    }
}
