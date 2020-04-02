/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

/**
 * Base Raft protocol exception.
 *
 * <p>This is the base exception type for all Raft protocol exceptions. Protocol exceptions must be
 * associated with a {@link RaftError.Type} which is used for more efficient serialization.
 */
public abstract class RaftException extends RuntimeException {

  private final RaftError.Type type;

  protected RaftException(final RaftError.Type type, final String message, final Object... args) {
    super(message != null ? String.format(message, args) : null);
    if (type == null) {
      throw new NullPointerException("type cannot be null");
    }
    this.type = type;
  }

  protected RaftException(
      final RaftError.Type type,
      final Throwable cause,
      final String message,
      final Object... args) {
    super(String.format(message, args), cause);
    if (type == null) {
      throw new NullPointerException("type cannot be null");
    }
    this.type = type;
  }

  protected RaftException(final RaftError.Type type, final Throwable cause) {
    super(cause);
    if (type == null) {
      throw new NullPointerException("type cannot be null");
    }
    this.type = type;
  }

  /**
   * Returns the exception type.
   *
   * @return The exception type.
   */
  public RaftError.Type getType() {
    return type;
  }

  public static class NoLeader extends RaftException {

    public NoLeader(final String message, final Object... args) {
      super(RaftError.Type.NO_LEADER, message, args);
    }
  }

  public static class IllegalMemberState extends RaftException {

    public IllegalMemberState(final String message, final Object... args) {
      super(RaftError.Type.ILLEGAL_MEMBER_STATE, message, args);
    }
  }

  public static class ApplicationException extends RaftException {

    public ApplicationException(final String message, final Object... args) {
      super(RaftError.Type.APPLICATION_ERROR, message, args);
    }

    public ApplicationException(final Throwable cause) {
      super(RaftError.Type.APPLICATION_ERROR, cause);
    }
  }

  public abstract static class OperationFailure extends RaftException {

    public OperationFailure(final RaftError.Type type, final String message, final Object... args) {
      super(type, message, args);
    }
  }

  public static class CommandFailure extends OperationFailure {

    public CommandFailure(final String message, final Object... args) {
      super(RaftError.Type.COMMAND_FAILURE, message, args);
    }
  }

  public static class QueryFailure extends OperationFailure {

    public QueryFailure(final String message, final Object... args) {
      super(RaftError.Type.QUERY_FAILURE, message, args);
    }
  }

  public static class UnknownClient extends RaftException {

    public UnknownClient(final String message, final Object... args) {
      super(RaftError.Type.UNKNOWN_CLIENT, message, args);
    }
  }

  public static class UnknownSession extends RaftException {

    public UnknownSession(final String message, final Object... args) {
      super(RaftError.Type.UNKNOWN_SESSION, message, args);
    }
  }

  public static class UnknownService extends RaftException {

    public UnknownService(final String message, final Object... args) {
      super(RaftError.Type.UNKNOWN_SERVICE, message, args);
    }
  }

  public static class ClosedSession extends RaftException {

    public ClosedSession(final String message, final Object... args) {
      super(RaftError.Type.CLOSED_SESSION, message, args);
    }
  }

  public static class ProtocolException extends RaftException {

    public ProtocolException(final String message, final Object... args) {
      super(RaftError.Type.PROTOCOL_ERROR, message, args);
    }
  }

  public static class ConfigurationException extends RaftException {

    public ConfigurationException(final String message, final Object... args) {
      super(RaftError.Type.CONFIGURATION_ERROR, message, args);
    }

    public ConfigurationException(
        final Throwable throwable, final String message, final Object... args) {
      super(RaftError.Type.CONFIGURATION_ERROR, throwable, message, args);
    }
  }

  public static class Unavailable extends RaftException {

    public Unavailable(final String message, final Object... args) {
      super(RaftError.Type.UNAVAILABLE, message, args);
    }

    public Unavailable(final Throwable cause) {
      super(RaftError.Type.UNAVAILABLE, cause);
    }
  }
}
