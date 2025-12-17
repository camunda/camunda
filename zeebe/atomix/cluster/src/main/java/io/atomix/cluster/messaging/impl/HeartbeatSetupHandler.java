/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static io.atomix.cluster.messaging.SbeUtil.*;

import io.atomix.cluster.messaging.BooleanType;
import io.atomix.cluster.messaging.HeartbeatSetupRequestDecoder;
import io.atomix.cluster.messaging.HeartbeatSetupRequestEncoder;
import io.atomix.cluster.messaging.HeartbeatSetupResponseDecoder;
import io.atomix.cluster.messaging.HeartbeatSetupResponseEncoder;
import io.atomix.cluster.messaging.MessageHeaderDecoder;
import io.atomix.cluster.messaging.MessageHeaderEncoder;
import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.atomix.utils.net.Address;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the heartbeat setup and payload negotiation during the connection handshake phase.
 *
 * <p>This handler implements a configurable heartbeat payload mechanism that allows both client and
 * server to negotiate whether heartbeat messages should include payloads. The negotiation happens
 * during the initial handshake, with both parties exchanging their heartbeat capabilities.
 *
 * <h2>Heartbeat Payload Negotiation Protocol</h2>
 *
 * <p>The negotiation follows this sequence:
 *
 * <ol>
 *   <li><b>Client Initiation:</b> The client sends a {@code HeartbeatSetupRequest} containing:
 *       <ul>
 *         <li>{@code heartbeatTimeout} - The timeout duration for heartbeat messages
 *         <li>{@code sendPayload} - Whether the client supports heartbeat payloads
 *       </ul>
 *   <li><b>Server Negotiation:</b> The server determines the final payload setting using AND logic:
 *       <pre>payloadEnabled = serverSupportsPayload AND clientSupportsPayload</pre>
 *       This ensures payloads are only enabled if <b>both</b> parties support them.
 *   <li><b>Server Response:</b> The server replies with a {@code HeartbeatSetupResponse}
 *       containing:
 *       <ul>
 *         <li>{@code heartbeatEnabled} - Whether the server has enabled heartbeats
 *         <li>{@code sendPayload} - The negotiated payload setting (same for both parties)
 *       </ul>
 *   <li><b>Handler Installation:</b> After successful negotiation, this handler removes itself and
 *       installs a {@link HeartbeatHandler} configured with the negotiated payload setting. <b>Both
 *       client and server use the same payload setting</b>, ensuring symmetrical behavior.
 * </ol>
 *
 * <h2>Backward Compatibility</h2>
 *
 * <p>The implementation maintains backward compatibility with older clients using SBE schema
 * version 2:
 *
 * <ul>
 *   <li>The {@code sendPayload} field is optional in both request and response messages
 *   <li>When absent, the field defaults to {@code BooleanType.NULL}, which is treated as {@code
 *       false} (no payload support)
 *   <li>Older clients that don't send the {@code sendPayload} field will receive heartbeats without
 *       payloads
 *   <li>The server gracefully handles decode failures by disabling heartbeats for that connection
 * </ul>
 *
 * <h2>Configuration Combinations</h2>
 *
 * <p>The following table shows all possible configuration combinations and their outcomes:
 *
 * <table border="1">
 *   <tr>
 *     <th>Client Config</th>
 *     <th>Server Config</th>
 *     <th>Negotiated Outcome</th>
 *   </tr>
 *   <tr>
 *     <td>Enabled</td>
 *     <td>Enabled</td>
 *     <td><b>Both</b> send heartbeats with payloads</td>
 *   </tr>
 *   <tr>
 *     <td>Enabled</td>
 *     <td>Disabled</td>
 *     <td><b>Both</b> send heartbeats without payloads</td>
 *   </tr>
 *   <tr>
 *     <td>Disabled</td>
 *     <td>Enabled</td>
 *     <td><b>Both</b> send heartbeats without payloads</td>
 *   </tr>
 *   <tr>
 *     <td>Disabled</td>
 *     <td>Disabled</td>
 *     <td><b>Both</b> send heartbeats without payloads</td>
 *   </tr>
 * </table>
 *
 * <p><b>Important:</b> The negotiation ensures that both parties always use the same payload
 * setting. There is no scenario where one party sends payloads while the other doesn't.
 *
 * <h2>Message Identification</h2>
 *
 * <p>Heartbeat messages can be identified by inspecting their {@code schemaId} field in the SBE
 * message header, allowing handlers to distinguish between heartbeat messages and regular protocol
 * messages.
 *
 * @see HeartbeatHandler
 * @see HeartbeatSetupRequestEncoder
 * @see HeartbeatSetupResponseEncoder
 */
public abstract sealed class HeartbeatSetupHandler extends ChannelDuplexHandler {

  private static final String HEARTBEAT_SETUP_SUBJECT = "internal-heartbeat-setup";
  protected final String afterHandler;
  protected final Logger log;
  protected final boolean forwardHeartbeats;
  protected final boolean sendHeartbeatPayload;
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  protected HeartbeatSetupHandler(
      final String afterHandler,
      final Logger log,
      final boolean forwardHeartbeats,
      final boolean sendHeartbeatPayload) {
    this.afterHandler = afterHandler;
    this.log = log;
    this.forwardHeartbeats = forwardHeartbeats;
    this.sendHeartbeatPayload = sendHeartbeatPayload;
  }

  /**
   * Client-side handler that initiates the heartbeat setup and payload negotiation.
   *
   * <p>The client handler performs the following steps:
   *
   * <ol>
   *   <li>Sends a {@code HeartbeatSetupRequest} to the server when the channel becomes active,
   *       indicating:
   *       <ul>
   *         <li>The desired heartbeat timeout
   *         <li>Whether the client supports sending heartbeats with payloads
   *       </ul>
   *   <li>Waits for a {@code HeartbeatSetupResponse} from the server
   *   <li>Analyzes the server's response to determine:
   *       <ul>
   *         <li>If heartbeats are enabled on the server
   *         <li>If the server will send heartbeats with payloads
   *       </ul>
   *   <li>If heartbeats are enabled, installs a {@link HeartbeatHandler.Client} with the negotiated
   *       payload settings
   *   <li>Removes itself from the pipeline after setup is complete
   * </ol>
   *
   * <p><b>Payload Negotiation:</b> The client uses the negotiated {@code sendPayload} value from
   * the server's response to configure its {@link HeartbeatHandler.Client}. This ensures both
   * client and server use the same payload setting - either both send payloads or both don't.
   *
   * @see HeartbeatHandler.Client
   */
  public static final class Client extends HeartbeatSetupHandler {

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    final AtomicLong messageIdGenerator;
    private final Address advertisedAddress;
    private final Duration heartbeatTimeout;
    private final Duration heartbeatInterval;
    private final HeartbeatSetupRequestEncoder requestEncoder = new HeartbeatSetupRequestEncoder();
    private final HeartbeatSetupResponseDecoder responseDecoder =
        new HeartbeatSetupResponseDecoder();
    private long heartbeatRequestId;

    public Client(
        final String afterHandler,
        final AtomicLong messageIdGenerator,
        final Address advertisedAddress,
        final Duration heartbeatTimeout,
        final Duration heartbeatInterval,
        final boolean forwardHeartbeats,
        final boolean sendHeartbeatPayload) {
      super(afterHandler, LOG, forwardHeartbeats, sendHeartbeatPayload);
      this.messageIdGenerator = messageIdGenerator;
      this.advertisedAddress = advertisedAddress;
      this.heartbeatTimeout = heartbeatTimeout;
      this.heartbeatInterval = heartbeatInterval;
      log.debug(
          "Creating HeartbeatSetupHandler.Client from {} with sendHeartbeatPayload={}",
          advertisedAddress,
          sendHeartbeatPayload);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
      if (ctx.channel().isActive()) {
        ctx.writeAndFlush(createHeartbeatRequest(sendHeartbeatPayload));
      }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      // check if it's the response to our HeartbeatRequest
      if (msg instanceof final ProtocolReply reply && reply.id() == heartbeatRequestId) {
        final var decoder = responseDecoder(reply.payload());
        boolean isHeartbeatEnabled = false;
        boolean isPayloadEnabled = false;
        if (decoder != null) {
          isHeartbeatEnabled = toBoolean(decoder.heartbeatEnabled());
          isPayloadEnabled = toBoolean(decoder.sendPayload());
        }
        log.trace(
            "Received HeartbeatResponse: id={}, heartbeatEnabled={}, isPayloadEnabled={}",
            reply.id(),
            isHeartbeatEnabled,
            isPayloadEnabled);
        if (isHeartbeatEnabled) {
          ctx.pipeline()
              .addAfter(
                  afterHandler,
                  HeartbeatHandler.HEARTBEAT_HANDLER_NAME,
                  new HeartbeatHandler.Client(
                      log,
                      messageIdGenerator,
                      advertisedAddress,
                      heartbeatTimeout,
                      heartbeatInterval,
                      forwardHeartbeats,
                      isPayloadEnabled));
        }
        // the heartbeat setup is done, no need to stay in the pipeline
        ctx.pipeline().remove(this);
        ReferenceCountUtil.release(msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    // The message is fixed in size, so we can allocate an exact buffer
    private byte[] allocateRequestBuffer() {
      return new byte
          [MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatSetupRequestEncoder.BLOCK_LENGTH];
    }

    /**
     * Creates a heartbeat setup request message with the client's payload preference.
     *
     * <p>This method constructs a {@code HeartbeatSetupRequest} containing:
     *
     * <ul>
     *   <li>{@code heartbeatTimeout} - The client's preferred heartbeat timeout in milliseconds
     *   <li>{@code sendPayload} - Whether the client supports heartbeat payloads
     * </ul>
     *
     * <p>The {@code sendPayload} field uses the SBE encoding:
     *
     * <ul>
     *   <li>{@code BooleanType.TRUE} - Client supports payloads
     *   <li>{@code BooleanType.FALSE} - Client does not support payloads
     *   <li>{@code BooleanType.NULL} - Default value for backward compatibility (treated as false)
     * </ul>
     *
     * @param sendPayload whether the client wants to enable heartbeat payloads
     * @return a protocol request ready to be sent to the server
     */
    private ProtocolRequest createHeartbeatRequest(final boolean sendPayload) {
      final var bytes = allocateRequestBuffer();
      final var buffer = new UnsafeBuffer(bytes);
      requestEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
      requestEncoder.heartbeatTimeout(heartbeatTimeout.toMillis()).sendPayload(toSBE(sendPayload));
      heartbeatRequestId = messageIdGenerator.incrementAndGet();
      return new ProtocolRequest(
          heartbeatRequestId, advertisedAddress, HEARTBEAT_SETUP_SUBJECT, bytes);
    }

    /**
     * Decodes the heartbeat setup response from the server.
     *
     * <p>Attempts to decode a {@code HeartbeatSetupResponse} message containing:
     *
     * <ul>
     *   <li>{@code heartbeatEnabled} - Whether the server has enabled heartbeats
     *   <li>{@code sendPayload} - The negotiated payload setting (same for both client and server)
     * </ul>
     *
     * <p><b>Backward Compatibility:</b> If the response cannot be decoded (e.g., from an older
     * server version), this method returns {@code null} and logs a warning. The calling code will
     * treat this as heartbeats disabled, ensuring graceful degradation.
     *
     * @param bytes the raw response payload bytes
     * @return decoded response, or {@code null} if decoding fails
     */
    private HeartbeatSetupResponseDecoder responseDecoder(final byte[] bytes) {
      final var buffer = new UnsafeBuffer(bytes);
      try {
        responseDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return responseDecoder;
      } catch (final IllegalStateException e) {
        log.info("Unable to decode heartbeat response, heartbeats are disabled.", e);
        return null;
      }
    }
  }

  /**
   * Server-side handler that responds to heartbeat setup requests and performs payload negotiation.
   *
   * <p>The server handler performs the following steps:
   *
   * <ol>
   *   <li>Waits for a {@code HeartbeatSetupRequest} from a connecting client
   *   <li>Extracts the client's heartbeat configuration:
   *       <ul>
   *         <li>{@code heartbeatTimeout} - How long to wait before considering the connection dead
   *         <li>{@code sendPayload} - Whether the client supports receiving heartbeats with
   *             payloads
   *       </ul>
   *   <li>Determines if the server should send payloads based on:
   *       <ul>
   *         <li>Server's own payload configuration ({@code sendHeartbeatPayload})
   *         <li>Client's payload support (from request)
   *         <li>Payload enabled = {@code sendHeartbeatPayload AND client supports payloads}
   *       </ul>
   *   <li>If heartbeat timeout > 0, installs a {@link HeartbeatHandler.Server} with the negotiated
   *       payload settings
   *   <li>Sends a {@code HeartbeatSetupResponse} to the client indicating:
   *       <ul>
   *         <li>{@code heartbeatEnabled = TRUE} (if timeout > 0)
   *         <li>{@code sendPayload} - The negotiated payload setting
   *       </ul>
   *   <li>Removes itself from the pipeline after setup is complete
   * </ol>
   *
   * <p><b>Payload Negotiation Logic:</b> The server negotiates the payload setting using AND logic:
   *
   * <pre>
   * payloadEnabled = server.sendHeartbeatPayload AND client.sendPayload
   * </pre>
   *
   * <p>This negotiated setting is then used by both parties, ensuring symmetric behavior. Both the
   * server's {@link HeartbeatHandler.Server} and the client's {@link HeartbeatHandler.Client} will
   * use the same payload configuration - either both send payloads or both don't.
   *
   * @see HeartbeatHandler.Server
   */
  public static final class Server extends HeartbeatSetupHandler {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private final HeartbeatSetupRequestDecoder requestDecoder = new HeartbeatSetupRequestDecoder();
    private final HeartbeatSetupResponseEncoder responseEncoder =
        new HeartbeatSetupResponseEncoder();

    public Server(
        final String afterHandler,
        final boolean forwardHeartbeats,
        final boolean sendHeartbeatPayload) {
      super(afterHandler, LOG, forwardHeartbeats, sendHeartbeatPayload);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      // check if it's a heartbeat request
      if (msg instanceof final ProtocolRequest request
          && request.subject().equals(HEARTBEAT_SETUP_SUBJECT)) {
        final var decoder = heartbeatTimeout(request.payload());
        long timeout = 0;
        boolean sendPayloadInRequest = false;
        if (decoder != null) {
          timeout = decoder.heartbeatTimeout();
          sendPayloadInRequest = toBoolean(decoder.sendPayload());
        }
        final var sendPayload = sendHeartbeatPayload && sendPayloadInRequest;
        if (timeout > 0) {
          log.trace(
              "Received heartbeat request: id:{}, heartbeatTimeout={} millis, sendPayload={}: server will send payload? {}",
              request.id(),
              timeout,
              sendPayloadInRequest,
              sendPayload);
          ctx.pipeline()
              .addAfter(
                  afterHandler,
                  HeartbeatHandler.HEARTBEAT_HANDLER_NAME,
                  new HeartbeatHandler.Server(
                      log, Duration.ofMillis(timeout), forwardHeartbeats, sendPayload));
        }
        // the heartbeat setup is done, no need to stay in the pipeline
        ctx.pipeline().remove(this);
        ctx.writeAndFlush(heartbeatResponse(request.id(), sendPayload));

        ReferenceCountUtil.release(msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    /**
     * Decodes the heartbeat setup request from the client.
     *
     * <p>Attempts to decode a {@code HeartbeatSetupRequest} message containing:
     *
     * <ul>
     *   <li>{@code heartbeatTimeout} - The client's requested heartbeat timeout (u32 on wire)
     *   <li>{@code sendPayload} - Whether the client supports heartbeat payloads
     * </ul>
     *
     * <p><b>Backward Compatibility:</b> If the request cannot be decoded (e.g., from an older
     * client version or malformed message), this method returns {@code null} and logs a warning.
     * The calling code will disable heartbeats for this connection, ensuring safe fallback
     * behavior.
     *
     * @param payload the raw request payload bytes
     * @return decoded request, or {@code null} if decoding fails
     */
    private HeartbeatSetupRequestDecoder heartbeatTimeout(final byte[] payload) {
      final var buffer = new UnsafeBuffer(payload);
      try {
        requestDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        // it's a u32 on the wire
        return requestDecoder;
      } catch (final IllegalStateException e) {
        log.warn("Unable to decode heartbeat request from client, heartbeats are disabled.", e);
        return null;
      }
    }

    /**
     * Creates a heartbeat setup response message with the negotiated payload setting.
     *
     * <p>This method constructs a {@code HeartbeatSetupResponse} containing:
     *
     * <ul>
     *   <li>{@code heartbeatEnabled = TRUE} - Indicates heartbeats are enabled on the server
     *   <li>{@code sendPayload} - The negotiated payload setting (same for both client and server)
     * </ul>
     *
     * <p><b>Negotiation Logic:</b> The {@code sendPayload} parameter represents the negotiated
     * outcome:
     *
     * <pre>
     * sendPayload = server.sendHeartbeatPayload AND client.sendPayload
     * </pre>
     *
     * <p>This ensures both parties use the same payload setting. The server sends this negotiated
     * value to the client, which then configures its {@link HeartbeatHandler.Client} with the same
     * setting, guaranteeing symmetric behavior.
     *
     * @param id of the request being responded to
     * @param sendPayload the negotiated payload setting (true only if both parties support
     *     payloads)
     * @return a protocol reply ready to be sent to the client
     */
    private ProtocolReply heartbeatResponse(final long id, final boolean sendPayload) {
      final var bytes = allocateResponseBuffer();
      final var buffer = new UnsafeBuffer(bytes);
      responseEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
      responseEncoder.heartbeatEnabled(BooleanType.TRUE);
      responseEncoder.sendPayload(toSBE(sendPayload));
      return new ProtocolReply(id, bytes, Status.OK);
    }

    private byte[] allocateResponseBuffer() {
      return new byte
          [MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatSetupResponseEncoder.BLOCK_LENGTH];
    }
  }
}
