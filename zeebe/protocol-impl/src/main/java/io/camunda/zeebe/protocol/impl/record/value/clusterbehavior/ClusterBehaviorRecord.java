/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clusterbehavior;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterBehaviorRecordValue;

/**
 * The behavior version commit. A marker without properties for now: its properties are defined in
 * #68930.
 */
public final class ClusterBehaviorRecord extends UnifiedRecordValue
    implements ClusterBehaviorRecordValue {

  public ClusterBehaviorRecord() {
    super(0);
  }
}
