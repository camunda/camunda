/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const UNITS = ['B', 'KiB', 'MiB', 'GiB'];
const BYTES_BASE = 1024;

function toHumanReadableBytes(bytes: number): string {
	if (bytes === 0) {
		return '0 B';
	}

	if (!Number.isFinite(bytes)) {
		return 'N/A';
	}

	const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(BYTES_BASE)), UNITS.length - 1);
	const value = bytes / Math.pow(BYTES_BASE, exponent);
	const unit = UNITS[exponent];
	const formatted = value.toFixed(2).replace(/\.?0+$/, '');

	return `${formatted} ${unit}`;
}

export {toHumanReadableBytes};
