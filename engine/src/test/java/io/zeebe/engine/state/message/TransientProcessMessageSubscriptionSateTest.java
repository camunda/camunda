package io.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.message.TransientProcessMessageSubscriptionState.Entry;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class TransientProcessMessageSubscriptionSateTest {

  public static final String MESSAGE_NAME = "message";

  private ProcessMessageSubscriptionRecord createProcessMessageSubscriptionRecord(
      final long elementInstanceKey, final String messageName) {
    final var result = new ProcessMessageSubscriptionRecord();
    result.setElementInstanceKey(elementInstanceKey);
    result.setMessageName(BufferUtil.wrapString(messageName));
    result.setSubscriptionPartitionId(1);
    result.setProcessInstanceKey(1);

    return result;
  }

  @Nested
  public class EntryTests {
    @Test
    public void entriesShouldSortInAscendingCommandSendTime() {
      // given
      final Entry entry1 = new Entry(createProcessMessageSubscriptionRecord(1, MESSAGE_NAME), 0);
      final Entry entry2 = new Entry(createProcessMessageSubscriptionRecord(1, MESSAGE_NAME), 1);

      // then
      assertThat(entry1).isLessThan(entry2);
      assertThat(entry2).isGreaterThan(entry1);
    }

    @Test
    public void entriesShouldBeEqualIfTheyReferToTheSameSubscription() {
      // given
      final Entry entry1 = new Entry(createProcessMessageSubscriptionRecord(1, MESSAGE_NAME), 0);
      final Entry entry2 = new Entry(createProcessMessageSubscriptionRecord(1, MESSAGE_NAME), 1);

      // then
      assertThat(entry1).isEqualTo(entry2);
    }

    @Test
    public void entriesMustNotBeEqualIfTheyReferTodifferentSubscriptions() {
      // given
      final Entry entry1 = new Entry(createProcessMessageSubscriptionRecord(1, MESSAGE_NAME), 0);
      final Entry entry2 = new Entry(createProcessMessageSubscriptionRecord(1, "otherMessage"), 0);
      final Entry entry3 = new Entry(createProcessMessageSubscriptionRecord(2, MESSAGE_NAME), 0);

      // then
      assertThat(entry1).isNotEqualTo(entry2);
      assertThat(entry1).isNotEqualTo(entry3);
      assertThat(entry2).isNotEqualTo(entry1);
      assertThat(entry2).isNotEqualTo(entry3);
      assertThat(entry3).isNotEqualTo(entry1);
      assertThat(entry3).isNotEqualTo(entry2);
    }
  }
}
