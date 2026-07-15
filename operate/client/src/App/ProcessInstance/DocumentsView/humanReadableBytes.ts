/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const UNITS = ['B', 'KiB', 'MiB', 'GiB'];
const BYTES_BASE = 1024;

/**
 * Formats a number of bytes into a human readable string with binary prefixes (up to GiB)
 * @param bytes - The number of bytes to format
 * @returns Formatted string (e.g. "1.5 MiB")
 */
function toHumanReadableBytes(bytes: number): string {
  if (bytes === 0) {
    return '0 B';
  }

  if (!Number.isFinite(bytes)) {
    return 'N/A';
  }

  const exponent = Math.min(
    Math.floor(Math.log(bytes) / Math.log(BYTES_BASE)),
    UNITS.length - 1,
  );

  const value = bytes / Math.pow(BYTES_BASE, exponent);
  const unit = UNITS[exponent];
  const formatted = value.toFixed(2).replace(/\.?0+$/, '');

  return `${formatted} ${unit}`;
}

export {toHumanReadableBytes};
