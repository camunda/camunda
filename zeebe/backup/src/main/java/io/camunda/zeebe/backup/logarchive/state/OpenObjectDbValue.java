/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;

/**
 * Value of {@code LOG_ARCHIVE_OPEN_OBJECTS}: the log object and the checkpoints that were attached
 * to it when it was archived (see {@code LogArchiveEventApplier}).
 */
final class OpenObjectDbValue extends UnpackedObject implements DbValue {

  private final ObjectProperty<LogObject> objectProperty =
      new ObjectProperty<>("object", new LogObject());
  private final ArrayProperty<CoveredCheckpoint> checkpointsProperty =
      new ArrayProperty<>("checkpoints", CoveredCheckpoint::new);

  OpenObjectDbValue() {
    super(2);
    declareProperty(objectProperty).declareProperty(checkpointsProperty);
  }

  LogObject object() {
    return objectProperty.getValue();
  }

  ArrayProperty<CoveredCheckpoint> checkpoints() {
    return checkpointsProperty;
  }
}
