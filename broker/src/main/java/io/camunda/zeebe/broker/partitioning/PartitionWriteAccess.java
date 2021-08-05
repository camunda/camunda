package io.camunda.zeebe.broker.partitioning;

import java.util.function.BiConsumer;

public interface PartitionWriteAccess {

  void sendMessage(Message message);

  void writeCommandToPartition(Command command, BiConsumer<Response, Exception> callback);
}
