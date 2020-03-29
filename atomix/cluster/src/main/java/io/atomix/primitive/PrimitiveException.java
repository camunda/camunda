/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.atomix.primitive;

import io.atomix.utils.AtomixRuntimeException;

/** Top level exception for Store failures. */
@SuppressWarnings("serial")
public class PrimitiveException extends AtomixRuntimeException {
  public PrimitiveException() {}

  public PrimitiveException(final String message) {
    super(message);
  }

  public PrimitiveException(final Throwable t) {
    super(t);
  }

  /** Store is temporarily unavailable. */
  public static class Unavailable extends PrimitiveException {
    public Unavailable() {}

    public Unavailable(final String message) {
      super(message);
    }
  }

  /** Store operation timeout. */
  public static class Timeout extends PrimitiveException {}

  /** Store update conflicts with an in flight transaction. */
  public static class ConcurrentModification extends PrimitiveException {}

  /** Store operation interrupted. */
  public static class Interrupted extends PrimitiveException {}

  /** Primitive service exception. */
  public static class ServiceException extends PrimitiveException {
    public ServiceException() {}

    public ServiceException(final String message) {
      super(message);
    }

    public ServiceException(final Throwable cause) {
      super(cause);
    }
  }

  /** Command failure exception. */
  public static class CommandFailure extends PrimitiveException {
    public CommandFailure() {}

    public CommandFailure(final String message) {
      super(message);
    }
  }

  /** Query failure exception. */
  public static class QueryFailure extends PrimitiveException {
    public QueryFailure() {}

    public QueryFailure(final String message) {
      super(message);
    }
  }

  /** Unknown client exception. */
  public static class UnknownClient extends PrimitiveException {
    public UnknownClient() {}

    public UnknownClient(final String message) {
      super(message);
    }
  }

  /** Unknown session exception. */
  public static class UnknownSession extends PrimitiveException {
    public UnknownSession() {}

    public UnknownSession(final String message) {
      super(message);
    }
  }

  /** Unknown service exception. */
  public static class UnknownService extends PrimitiveException {
    public UnknownService() {}

    public UnknownService(final String message) {
      super(message);
    }
  }

  /** Closed session exception. */
  public static class ClosedSession extends PrimitiveException {
    public ClosedSession() {}

    public ClosedSession(final String message) {
      super(message);
    }
  }
}
