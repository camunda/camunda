/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useMatchRoute, useRouterState} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {SidebarNode} from '@camunda/design-system';
import type {AuthenticatedCurrentUser} from '@camunda/camunda-api-zod-schemas/8.11';
import {hasComponentAccess} from '#/shared/componentAccess';
import {getOperateSidebarItems} from './operateSidebarItems';

function useOperateSidebarNavigation({authorizedComponents}: AuthenticatedCurrentUser): SidebarNode[] {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const isOperationsRoute =
		matchRoute({to: '/operate/operations-log', fuzzy: true}) !== false ||
		matchRoute({to: '/operate/batch-operations', fuzzy: true}) !== false;
	const pathname = useRouterState({select: ({location}) => location.pathname});
	const [toggle, setToggle] = useState<{pathname: string; isExpanded: boolean} | undefined>();
	const isOperationsExpanded = toggle?.pathname === pathname ? toggle.isExpanded : isOperationsRoute;

	if (toggle !== undefined && toggle.pathname !== pathname) {
		setToggle(undefined);
	}

	if (!hasComponentAccess('operate', authorizedComponents)) {
		return [];
	}

	return getOperateSidebarItems({
		t,
		matchRoute,
		base: '/operate',
		operationsGroup: {
			isExpanded: isOperationsExpanded,
			onToggleExpand: (isExpanded) => setToggle({pathname, isExpanded}),
		},
	});
}

export {useOperateSidebarNavigation};
