/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import java.util.List;

/**
 * Sparse index of a log object.
 *
 * @param entries the first frame and every 64th frame, in object order
 * @param footerOffset absolute offset of the footer in the object, i.e. the end of the frames
 */
public record LogObjectFooter(List<Entry> entries, long footerOffset) {

  /**
   * @param lowestPosition lowest position of the frame, -1 for non-application frames
   * @param frameOffset absolute offset of the frame in the object
   */
  public record Entry(long index, long lowestPosition, long frameOffset) {}
}
