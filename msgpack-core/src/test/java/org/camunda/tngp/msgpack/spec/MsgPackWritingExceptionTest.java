package org.camunda.tngp.msgpack.spec;
import java.util.Arrays;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackUtil.CheckedConsumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MsgPackWritingExceptionTest
{

    protected static final int BUFFER_CAPACITY = 1024;
    protected static final int WRITE_OFFSET = 123;
    protected MutableDirectBuffer actualValueBuffer = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
    protected static final String NEGATIVE_BUF_SIZE_EXCEPTION_MSG = "Negative value should not be accepted by size value and unsiged 64bit integer";


    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
            {
                NEGATIVE_BUF_SIZE_EXCEPTION_MSG,
                codeUnderTest((r) -> r.writeArrayHeader(-1))
            },
            {
                NEGATIVE_BUF_SIZE_EXCEPTION_MSG,
                codeUnderTest((r) -> r.writeBinaryHeader(-1))
            },
            {
                NEGATIVE_BUF_SIZE_EXCEPTION_MSG,
                codeUnderTest((r) -> r.writeMapHeader(-1))
            },
            {
                NEGATIVE_BUF_SIZE_EXCEPTION_MSG,
                codeUnderTest((r) -> r.writeStringHeader(-1))
            }
        });
    }

    @Parameter(0)
    public String expectedExceptionMessage;

    @Parameter(1)
    public CheckedConsumer<MsgPackWriter> codeUnderTest;



    @Before
    public void setUp()
    {
        Arrays.fill(actualValueBuffer.byteArray(), (byte) 0);
    }

    @Test
    public void shouldNotReadNegativeSize() throws Exception
    {
        // given
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(actualValueBuffer, WRITE_OFFSET);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage(expectedExceptionMessage);

        // when
        codeUnderTest.accept(writer);
    }


    protected static CheckedConsumer<MsgPackWriter> codeUnderTest(CheckedConsumer<MsgPackWriter> arg)
    {
        return arg;
    }
}
