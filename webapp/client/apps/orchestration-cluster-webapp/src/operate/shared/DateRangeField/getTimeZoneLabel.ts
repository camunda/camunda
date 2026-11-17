/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const getTimeZoneLabel = () => {
	const {timeZone} = Intl.DateTimeFormat().resolvedOptions();
	const offset = new Intl.DateTimeFormat(undefined, {timeZoneName: 'longOffset'})
		.formatToParts(new Date())
		.find(({type}) => type === 'timeZoneName')?.value;
	return offset === undefined ? timeZone : `${timeZone} (${offset})`;
};

export {getTimeZoneLabel};
