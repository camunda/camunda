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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.PrimitiveException;

/**
 * Base type for Raft protocol errors.
 *
 * <p>Raft errors are passed on the wire in lieu of exceptions to reduce the overhead of
 * serialization. Each error is identifiable by an error ID which is used to serialize and
 * deserialize errors.
 */
public class RaftError {

  private final Type type;
  private final String message;

  public RaftError(final Type type, final String message) {
    this.type = checkNotNull(type, "type cannot be null");
    this.message = message;
  }

  /**
   * Returns the error type.
   *
   * @return The error type.
   */
  public Type type() {
    return type;
  }

  /**
   * Returns the error message.
   *
   * @return The error message.
   */
  public String message() {
    return message;
  }

  /**
   * Creates a new exception for the error.
   *
   * @return The error exception.
   */
  public PrimitiveException createException() {
    return type.createException(message);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("type", type).add("message", message).toString();
  }

  /** Raft error types. */
  public enum Type {

    /** No leader error. */
    NO_LEADER {
      @Override
      PrimitiveException createException() {
        return createException("Failed to locate leader");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.Unavailable(message) : createException();
      }
    },

    /** Read application error. */
    QUERY_FAILURE {
      @Override
      PrimitiveException createException() {
        return createException("Failed to obtain read quorum");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.QueryFailure(message) : createException();
      }
    },

    /** Write application error. */
    COMMAND_FAILURE {
      @Override
      PrimitiveException createException() {
        return createException("Failed to obtain write quorum");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.CommandFailure(message) : createException();
      }
    },

    /** User application error. */
    APPLICATION_ERROR {
      @Override
      PrimitiveException createException() {
        return createException("An application error occurred");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null
            ? new PrimitiveException.ServiceException(message)
            : createException();
      }
    },

    /** Illegal member state error. */
    ILLEGAL_MEMBER_STATE {
      @Override
      PrimitiveException createException() {
        return createException("Illegal member state");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.Unavailable(message) : createException();
      }
    },

    /** Unknown client error. */
    UNKNOWN_CLIENT {
      @Override
      PrimitiveException createException() {
        return createException("Unknown client");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.UnknownClient(message) : createException();
      }
    },

    /** Unknown session error. */
    UNKNOWN_SESSION {
      @Override
      PrimitiveException createException() {
        return createException("Unknown member session");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.UnknownSession(message) : createException();
      }
    },

    /** Unknown state machine error. */
    UNKNOWN_SERVICE {
      @Override
      PrimitiveException createException() {
        return createException("Unknown primitive service");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.UnknownService(message) : createException();
      }
    },

    /** Closed session error. */
    CLOSED_SESSION {
      @Override
      PrimitiveException createException() {
        return createException("Closed session");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.ClosedSession(message) : createException();
      }
    },

    /** Internal error. */
    PROTOCOL_ERROR {
      @Override
      PrimitiveException createException() {
        return createException("Failed to reach consensus");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.Unavailable(message) : createException();
      }
    },

    /** Configuration error. */
    CONFIGURATION_ERROR {
      @Override
      PrimitiveException createException() {
        return createException("Configuration failed");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.Unavailable(message) : createException();
      }
    },

    /** Unavailable service error. */
    UNAVAILABLE {
      @Override
      PrimitiveException createException() {
        return createException("Service is unavailable");
      }

      @Override
      PrimitiveException createException(final String message) {
        return message != null ? new PrimitiveException.Unavailable(message) : createException();
      }
    };

    /**
     * Creates an exception with a default message.
     *
     * @return the exception
     */
    abstract PrimitiveException createException();

    /**
     * Creates an exception with the given message.
     *
     * @param message the exception message
     * @return the exception
     */
    abstract PrimitiveException createException(String message);
  }
}
