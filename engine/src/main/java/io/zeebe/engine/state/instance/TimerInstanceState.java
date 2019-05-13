/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.instance;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.engine.state.ZbColumnFamilies;
import java.util.function.Consumer;

public class TimerInstanceState {

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, TimerInstance>
      timerInstanceColumnFamily;
  private final TimerInstance timerInstance;
  private final DbLong timerKey;
  private final DbLong elementInstanceKey;
  private final DbCompositeKey<DbLong, DbLong> elementAndTimerKey;

  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbLong>>, DbNil>
      dueDateColumnFamily;
  private final DbLong dueDateKey;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbLong>> dueDateCompositeKey;

  private long nextDueDate;

  public TimerInstanceState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    timerInstance = new TimerInstance();
    timerKey = new DbLong();
    elementInstanceKey = new DbLong();
    elementAndTimerKey = new DbCompositeKey<>(elementInstanceKey, timerKey);
    timerInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TIMERS, dbContext, elementAndTimerKey, timerInstance);

    dueDateKey = new DbLong();
    dueDateCompositeKey = new DbCompositeKey<>(dueDateKey, elementAndTimerKey);
    dueDateColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TIMER_DUE_DATES, dbContext, dueDateCompositeKey, DbNil.INSTANCE);
  }

  public void put(TimerInstance timer) {
    timerKey.wrapLong(timer.getKey());
    elementInstanceKey.wrapLong(timer.getElementInstanceKey());

    timerInstanceColumnFamily.put(elementAndTimerKey, timer);

    dueDateKey.wrapLong(timer.getDueDate());
    dueDateColumnFamily.put(dueDateCompositeKey, DbNil.INSTANCE);
  }

  public long findTimersWithDueDateBefore(final long timestamp, TimerVisitor consumer) {
    nextDueDate = -1L;

    dueDateColumnFamily.whileTrue(
        (key, nil) -> {
          final DbLong dueDate = key.getFirst();

          boolean consumed = false;
          if (dueDate.getValue() <= timestamp) {
            final DbCompositeKey<DbLong, DbLong> elementAndTimerKey = key.getSecond();
            final TimerInstance timerInstance = timerInstanceColumnFamily.get(elementAndTimerKey);
            consumed = consumer.visit(timerInstance);
          }

          if (!consumed) {
            nextDueDate = dueDate.getValue();
          }
          return consumed;
        });

    return nextDueDate;
  }

  /**
   * NOTE: the timer instance given to the consumer is shared and will be mutated on the next
   * iteration.
   */
  public void forEachTimerForElementInstance(
      long elementInstanceKey, Consumer<TimerInstance> action) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);

    timerInstanceColumnFamily.whileEqualPrefix(
        this.elementInstanceKey,
        (key, value) -> {
          action.accept(value);
        });
  }

  public TimerInstance get(long elementInstanceKey, long timerKey) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.timerKey.wrapLong(timerKey);

    return timerInstanceColumnFamily.get(elementAndTimerKey);
  }

  public void remove(TimerInstance timer) {
    elementInstanceKey.wrapLong(timer.getElementInstanceKey());
    timerKey.wrapLong(timer.getKey());
    timerInstanceColumnFamily.delete(elementAndTimerKey);

    dueDateKey.wrapLong(timer.getDueDate());
    dueDateColumnFamily.delete(dueDateCompositeKey);
  }

  @FunctionalInterface
  public interface TimerVisitor {
    boolean visit(final TimerInstance timer);
  }
}
