/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

/**
 * An encoded segment.
 *
 * @param bytes the whole object
 * @param footer what the footer of the object says
 * @param sha256 lowercase hex SHA-256 of {@code bytes}
 */
public record EncodedSegment(byte[] bytes, SegmentFooter footer, String sha256) {}
