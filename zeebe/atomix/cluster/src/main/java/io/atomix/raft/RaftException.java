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

import io.atomix.raft.RaftError.Type;

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

  public static class ProtocolException extends RaftException {

    public ProtocolException(final String message, final Object... args) {
      super(RaftError.Type.PROTOCOL_ERROR, message, args);
    }
  }

  public static class ConfigurationException extends RaftException {

    public ConfigurationException(
        final Throwable throwable, final String message, final Object... args) {
      super(RaftError.Type.CONFIGURATION_ERROR, throwable, message, args);
    }
  }

  public static class CommitFailedException extends RaftException {

    public CommitFailedException(final String message, final Object... args) {
      super(Type.COMMAND_FAILURE, message, args);
    }
  }

  public static class AppendFailureException extends RaftException {

    private final long index;

    public AppendFailureException(final long index, final String message) {
      super(Type.COMMAND_FAILURE, message);
      this.index = index;
    }

    public long getIndex() {
      return index;
    }
  }
}
