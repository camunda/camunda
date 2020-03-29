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
package io.atomix.raft.utils;

import java.util.function.Consumer;

/**
 * Quorum helper. Completes and invokes a callback when the number of {@link #succeed()} or {@link
 * #fail()} calls equal the expected quorum count. Not threadsafe.
 */
public class Quorum {

  private final int quorum;
  private int succeeded = 1;
  private int failed;
  private Consumer<Boolean> callback;
  private boolean complete;

  public Quorum(final int quorum, final Consumer<Boolean> callback) {
    this.quorum = quorum;
    this.callback = callback;
  }

  /** Indicates that a call in the quorum succeeded. */
  public Quorum succeed() {
    succeeded++;
    checkComplete();
    return this;
  }

  private void checkComplete() {
    if (!complete && callback != null) {
      if (succeeded >= quorum) {
        complete = true;
        callback.accept(true);
      } else if (failed >= quorum) {
        complete = true;
        callback.accept(false);
      }
    }
  }

  /** Indicates that a call in the quorum failed. */
  public Quorum fail() {
    failed++;
    checkComplete();
    return this;
  }

  /**
   * Cancels the quorum. Once this method has been called, the quorum will be marked complete and
   * the handler will never be called.
   */
  public void cancel() {
    callback = null;
    complete = true;
  }
}
