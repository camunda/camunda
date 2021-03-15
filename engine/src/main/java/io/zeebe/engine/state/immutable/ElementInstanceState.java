/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.StoredRecord;
import java.util.List;

public interface ElementInstanceState {

  ElementInstance getInstance(long key);

  StoredRecord getStoredRecord(long recordKey);

  List<ElementInstance> getChildren(long parentKey);

  List<IndexedRecord> getDeferredRecords(long scopeKey);

  IndexedRecord getFailedRecord(long key);

  AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(long processInstanceKey);
}
