/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type OptionalFilter =
	| 'processInstanceKey'
	| 'parentProcessInstanceKey'
	| 'businessId'
	| 'batchOperationKey'
	| 'errorMessage'
	| 'hasRetriesLeft'
	| 'variable'
	| 'startDateRange'
	| 'endDateRange';

type OptionalFilterValues = {
	processInstanceKey?: string;
	parentProcessInstanceKey?: string;
	businessId?: string;
	batchOperationKey?: string;
	errorMessage?: string;
	hasRetriesLeft?: boolean;
	startDateFrom?: string;
	startDateTo?: string;
	endDateFrom?: string;
	endDateTo?: string;
};

type VariableFieldValues = {
	variableName?: string;
	variableValues?: string;
};

export type {OptionalFilter, OptionalFilterValues, VariableFieldValues};
