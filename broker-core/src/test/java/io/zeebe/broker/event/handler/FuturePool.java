/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event.handler;

import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;

public class FuturePool {
  protected List<CompletableActorFuture<?>> futures = new ArrayList<>();

  public <T> CompletableActorFuture<T> next() {
    final CompletableActorFuture<T> future = new CompletableActorFuture<>();
    futures.add(future);
    return future;
  }

  @SuppressWarnings("unchecked")
  public <T> CompletableActorFuture<T> at(int index) {
    return (CompletableActorFuture<T>) futures.get(index);
  }
}
