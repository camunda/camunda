/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Incident} from '@camunda/camunda-api-zod-schemas/8.11';

type EnhancedIncident = Incident & {
	processDefinitionName: string;
	elementName: string;
	isSelected: boolean;
};

type DecisionInstanceLookup = Record<string, {decisionInstanceKey: string; decisionDefinitionName: string}>;

type ChildInstanceWithIncident = {
	type: 'process' | 'decision';
	key: string;
	name: string;
};

export type {EnhancedIncident, DecisionInstanceLookup, ChildInstanceWithIncident};
