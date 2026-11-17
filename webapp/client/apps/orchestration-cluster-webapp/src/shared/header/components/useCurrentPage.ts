/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRouterState} from '@tanstack/react-router';

type CurrentPage =
	| 'dashboard'
	| 'processes'
	| 'decisions'
	| 'batch-operations'
	| 'operations-log'
	| 'process-details'
	| 'decision-details'
	| undefined;

function getCurrentPage(pathname: string): CurrentPage {
	const [, , section, detail] = pathname.replace(/\/$/, '').split('/');

	if (section === undefined) {
		return 'dashboard';
	}
	if (section === 'processes') {
		return detail === undefined ? 'processes' : 'process-details';
	}
	if (section === 'decisions') {
		return detail === undefined ? 'decisions' : 'decision-details';
	}
	if (section === 'batch-operations') {
		return 'batch-operations';
	}
	if (section === 'operations-log') {
		return 'operations-log';
	}

	return undefined;
}

function useCurrentPage() {
	const pathname = useRouterState({select: ({location}) => location.pathname});

	return {currentPage: getCurrentPage(pathname)};
}

export {useCurrentPage};
