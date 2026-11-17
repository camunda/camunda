/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type OptionalFilter = 'decisionEvaluationInstanceKey' | 'processInstanceKey' | 'businessId' | 'evaluationDateRange';

type OptionalFilterValues = {
	decisionEvaluationInstanceKey?: string;
	processInstanceKey?: string;
	businessId?: string;
	evaluationDateFrom?: string;
	evaluationDateTo?: string;
};

export type {OptionalFilter, OptionalFilterValues};
