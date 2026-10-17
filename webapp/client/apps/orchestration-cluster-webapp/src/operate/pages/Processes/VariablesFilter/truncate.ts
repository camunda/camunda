/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MAX_LENGTH = 50;
const OMISSION = '...';

function truncate(value: string) {
	const characters = Array.from(value);
	return characters.length > MAX_LENGTH
		? `${characters.slice(0, MAX_LENGTH - OMISSION.length).join('')}${OMISSION}`
		: value;
}

export {truncate};
