package io.camunda.zeebe.broker.partitioning;

import java.util.function.BiConsumer;

public interface PartitionQueryAccess {

  void runQuery(Query query, BiConsumer<QueryResponse, Exception> callback);
}
