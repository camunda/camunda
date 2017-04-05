package org.camunda.tngp.msgpack.spec;
import static org.camunda.tngp.msgpack.spec.MsgPackCodes.*;

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
    protected static final String NEGATIVE_BUF_SIZE_EXCEPTION_MSG = "Negative buffer size";


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

    @Test
    public void shouldNotReadNegativeSizeInReadMapHeader()
    {
        //given
        final DirectBuffer negativeSizeMapBuf = new UnsafeBuffer(new byte[]{ (byte) MAP32, (byte) 0xff, (byte) 0xff,  (byte) 0xff, (byte) 0xff });
        reader.wrap(negativeSizeMapBuf, 0, negativeSizeMapBuf.capacity());

        //then

        exception.expect(RuntimeException.class);
        exception.expectMessage(NEGATIVE_BUF_SIZE_EXCEPTION_MSG);

        //when
        reader.readMapHeader();
    }

    @Test
    public void shouldNotReadNegativeSizeInReadArrayHeader()
    {
        //given
        final DirectBuffer negativeSizeArrayBuf = new UnsafeBuffer(new byte[]{ (byte) ARRAY32, (byte) 0xff, (byte) 0xff,  (byte) 0xff, (byte) 0xff });
        reader.wrap(negativeSizeArrayBuf, 0, negativeSizeArrayBuf.capacity());

        //then

        exception.expect(RuntimeException.class);
        exception.expectMessage(NEGATIVE_BUF_SIZE_EXCEPTION_MSG);

        //when
        reader.readArrayHeader();
    }

    @Test
    public void shouldNotReadNegativeSizeInReadStringHeader()
    {
        //given
        final DirectBuffer negativeSizeStringBuf = new UnsafeBuffer(new byte[]{ (byte) STR32, (byte) 0xff, (byte) 0xff,  (byte) 0xff, (byte) 0xff });
        reader.wrap(negativeSizeStringBuf, 0, negativeSizeStringBuf.capacity());

        //then

        exception.expect(RuntimeException.class);
        exception.expectMessage(NEGATIVE_BUF_SIZE_EXCEPTION_MSG);

        //when
        reader.readStringLength();
    }

    @Test
    public void shouldNotReadNegativeSizeInReadBinaryHeader()
    {
        //given
        final DirectBuffer negativeSizeBinaryBuf = new UnsafeBuffer(new byte[]{ (byte) BIN32, (byte) 0xff, (byte) 0xff,  (byte) 0xff, (byte) 0xff });
        reader.wrap(negativeSizeBinaryBuf, 0, negativeSizeBinaryBuf.capacity());

        //then

        exception.expect(RuntimeException.class);
        exception.expectMessage(NEGATIVE_BUF_SIZE_EXCEPTION_MSG);

        //when
        reader.readBinaryLength();
    }



    protected static Consumer<MsgPackReader> codeUnderTest(Consumer<MsgPackReader> arg)
    {
        return arg;
    }
}
