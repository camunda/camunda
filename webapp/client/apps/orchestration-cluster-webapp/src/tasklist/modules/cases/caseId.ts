/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const CASE_PREFIX = 'case:';

function escapeLike(value: string): string {
	return value.replace(/[\\*?]/g, (match) => `\\${match}`);
}

export {CASE_PREFIX, escapeLike};
