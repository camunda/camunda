/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useState} from 'react';
import type {IncidentErrorType} from '@camunda/camunda-api-zod-schemas/8.11';

type IncidentsFilter = {
	errorTypes: IncidentErrorType[];
	setErrorTypes: (errorTypes: IncidentErrorType[]) => void;
};

const IncidentsFilterContext = createContext<IncidentsFilter | null>(null);

function useIncidentsFilterState(processInstanceId: string): IncidentsFilter {
	const [ownerId, setOwnerId] = useState(processInstanceId);
	const [errorTypes, setErrorTypes] = useState<IncidentErrorType[]>([]);
	if (ownerId !== processInstanceId) {
		setOwnerId(processInstanceId);
		setErrorTypes([]);
	}
	return {errorTypes, setErrorTypes};
}

function useIncidentsFilter() {
	const value = useContext(IncidentsFilterContext);
	if (value === null) {
		throw new Error('IncidentsFilterContext provider is required');
	}
	return value;
}

export {IncidentsFilterContext, useIncidentsFilterState, useIncidentsFilter};
