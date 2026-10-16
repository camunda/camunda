/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import java.util.Objects;

/**
 * A committed entry read by the {@link LogArchiver}: the frame that goes into the log object, plus
 * a side flag that is not part of the log object format.
 *
 * @param frame the journal entry as it is encoded into the log object
 * @param archiveOwn true if the entry carries nothing but the log archive's own bookkeeping: every
 *     record in it has the value type {@code LOG_ARCHIVE}, or it is not an application entry at all
 *     (e.g. a Raft initial or configuration entry). Such entries do not justify an eager cut, since
 *     archiving them produces new ones of the same kind (spec section 8.1).
 */
public record LogArchiverEntry(LogArchiveFrame frame, boolean archiveOwn) {

  public LogArchiverEntry {
    Objects.requireNonNull(frame, "frame");
  }
}
