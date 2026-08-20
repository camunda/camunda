/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';

function formatOperationType(type: string): string {
	return type
		.split('_')
		.map((word) => word.charAt(0) + word.slice(1).toLowerCase())
		.join(' ');
}

function formatStartDate(startDate: string | null | undefined): string {
	return startDate ? format(parseISO(startDate), 'yyyy-MM-dd HH:mm:ss') : '--';
}

export {formatOperationType, formatStartDate};
