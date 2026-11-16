/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;

/**
 * A record value whose storage ordinal can be set after construction. Every {@link
 * UnifiedRecordValue} that is {@link StorageOrdinalRelated} implements it, so engine code can set
 * the ordinal without knowing the concrete record type.
 */
public interface MutableStorageOrdinal extends StorageOrdinalRelated {

  /**
   * @param storageOrdinal the ordinal to store the record under
   * @return this record, for chaining
   */
  MutableStorageOrdinal setStorageOrdinal(int storageOrdinal);
}
