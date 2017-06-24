package io.zeebe.transport;

import static org.assertj.core.api.Assertions.fail;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Ignore("https://github.com/camunda-tngp/zb-transport/issues/25")
public class AsyncRequestControllerTest
{
    @Test
    public void explode()
    {
        fail("test request response controller");
    }

//    public static final int CHANNEL_ID = 10;
//    public static final int STREAM_ID = 21;
//    public static final int WRITER_LENGTH = 32;
//    public static final int REQUEST_OFFSET = 43;
//    public static final int RESPONSE_LENGTH = 54;
//
//    @Mock
//    protected ChannelManager channelManager;
//    @Mock
//    protected TransportConnectionPool connectionPool;
//    @Mock
//    protected AsyncRequestController requestController;
//
//    protected SocketAddress socketAddress = new SocketAddress("example.com", 1234);
//    protected CompletableFuture<Integer> future = new CompletableFuture<>();
//    @Mock
//    protected BufferWriter writer;
//    @Mock
//    protected BufferReader reader;
//
//    @Mock
//    protected PooledFuture<Channel> pooledFuture;
//    @Mock
//    protected ChannelImpl channel;
//    @Mock
//    protected TransportConnection connection;
//    @Mock
//    protected PooledTransportRequest request;
//    @Mock
//    protected MutableDirectBuffer requestBuffer;
//    @Mock
//    protected MutableDirectBuffer responseBuffer;
//
//    @Before
//    public void setUp()
//    {
//        MockitoAnnotations.initMocks(this);
//
//        when(writer.getLength()).thenReturn(WRITER_LENGTH);
//
//        requestController = new AsyncRequestController(channelManager, connectionPool);
//        requestController.configure(socketAddress, writer, reader, future);
//
//        when(channel.getStreamId()).thenReturn(STREAM_ID);
//
//        when(pooledFuture.isFailed()).thenReturn(false);
//        when(pooledFuture.poll()).thenReturn(channel);
//
//        when(channelManager.requestChannelAsync(eq(socketAddress))).thenReturn(pooledFuture);
//
//        when(request.getChannelId()).thenReturn(CHANNEL_ID);
//        when(request.getClaimedRequestBuffer()).thenReturn(requestBuffer);
//        when(request.getClaimedOffset()).thenReturn(REQUEST_OFFSET);
//        when(request.pollResponse()).thenReturn(true);
//        when(request.getResponseBuffer()).thenReturn(responseBuffer);
//        when(request.getResponseLength()).thenReturn(RESPONSE_LENGTH);
//
//        when(connection.openRequest(eq(STREAM_ID), eq(WRITER_LENGTH))).thenReturn(request);
//
//        when(connectionPool.openConnection()).thenReturn(connection);
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    public void testSendRequest()
//    {
//        // when
//        shouldTransition(
//            requestController.requestChannelState,
//            requestController.awaitChannelState,
//            requestController.openConnectionState,
//            requestController.openRequestState,
//            requestController.sendRequestState,
//            requestController.awaitResponseState,
//            requestController.handleResponseState,
//            requestController.closedState
//        );
//
//        // then
//        assertThat(future).isCompletedWithValue(CHANNEL_ID);
//
//        verify(writer).write(requestBuffer, REQUEST_OFFSET);
//        verify(reader).wrap(responseBuffer, 0, RESPONSE_LENGTH);
//    }
//
//    @Test
//    public void testConnectionFailure()
//    {
//        // given
//        when(pooledFuture.isFailed()).thenReturn(true);
//
//        // when
//        shouldTransition(
//            requestController.requestChannelState,
//            requestController.awaitChannelState,
//            requestController.failedState
//        );
//
//        // then
//        assertThat(future).hasFailedWithThrowableThat()
//                          .isInstanceOf(RuntimeException.class)
//                          .hasMessageContaining("Unable to connect");
//    }
//
//    @Test
//    public void testWriterFailure()
//    {
//        // given
//        doThrow(new RuntimeException("Unable to write"))
//            .when(writer).write(eq(requestBuffer), eq(REQUEST_OFFSET));
//
//        // when
//        shouldTransition(
//            requestController.requestChannelState,
//            requestController.awaitChannelState,
//            requestController.openConnectionState,
//            requestController.openRequestState,
//            requestController.sendRequestState,
//            requestController.failedState
//        );
//
//        // then
//        assertThat(future).hasFailedWithThrowableThat()
//                          .isInstanceOf(RuntimeException.class)
//                          .hasMessageContaining("Unable to write");
//    }
//
//    @Test
//    public void testResponseFailure()
//    {
//        // given
//        doThrow(new RuntimeException("Response failure"))
//            .when(request).pollResponse();
//
//        // when
//        shouldTransition(
//            requestController.requestChannelState,
//            requestController.awaitChannelState,
//            requestController.openConnectionState,
//            requestController.openRequestState,
//            requestController.sendRequestState,
//            requestController.awaitResponseState,
//            requestController.failedState
//        );
//
//        // then
//        assertThat(future).hasFailedWithThrowableThat()
//                          .isInstanceOf(RuntimeException.class)
//                          .hasMessageContaining("Response failure");
//    }
//
//    @Test
//    public void testReaderFailure()
//    {
//        // given
//        doThrow(new RuntimeException("Unable to read"))
//            .when(reader).wrap(eq(responseBuffer), eq(0), eq(RESPONSE_LENGTH));
//
//        // when
//        shouldTransition(
//            requestController.requestChannelState,
//            requestController.awaitChannelState,
//            requestController.openConnectionState,
//            requestController.openRequestState,
//            requestController.sendRequestState,
//            requestController.awaitResponseState,
//            requestController.handleResponseState,
//            requestController.failedState
//        );
//
//        // then
//        assertThat(future).hasFailedWithThrowableThat()
//                          .isInstanceOf(RuntimeException.class)
//                          .hasMessageContaining("Unable to read");
//    }
//
//    @Test
//    public void testBorrowChannel()
//    {
//        // given
//        shouldTransition(
//            requestController.requestChannelState,
//            requestController.awaitChannelState,
//            requestController.openConnectionState,
//            requestController.openRequestState,
//            requestController.sendRequestState,
//            requestController.awaitResponseState,
//            requestController.handleResponseState
//        );
//
//        // when
//        final Channel channel = requestController.borrowChannel();
//
//        // then
//        assertThat(channel).isEqualTo(this.channel);
//
//        verify(this.channel).countUsageBegin();
//    }
//
//
//    protected void shouldTransition(final State... states)
//    {
//        final StateMachine<AsyncRequestController.Context> stateMachine = requestController.stateMachine;
//
//        for (final State state : states)
//        {
//            assertThat(stateMachine.getCurrentState()).isEqualTo(state);
//            requestController.doWork();
//        }
//    }

}
