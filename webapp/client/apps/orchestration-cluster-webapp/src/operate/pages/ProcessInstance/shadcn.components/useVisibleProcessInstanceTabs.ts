/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useLocation, useNavigate} from '@tanstack/react-router';
import {
	getHiddenTabRedirect,
	getProcessInstanceTabFromPath,
	getVisibleProcessInstanceTabs,
} from './processInstanceTabs';

type Options = {
	processInstanceId: string;
	hasSelection: boolean;
	isDesktop: boolean;
	isProcessLevelWaiting: boolean;
	isWaitStateLoading: boolean;
};

function useVisibleProcessInstanceTabs({
	processInstanceId,
	hasSelection,
	isDesktop,
	isProcessLevelWaiting,
	isWaitStateLoading,
}: Options) {
	const navigate = useNavigate();
	const {pathname} = useLocation();
	const visibleTabs = getVisibleProcessInstanceTabs(hasSelection, !isDesktop);
	const activeTab = getProcessInstanceTabFromPath(pathname);
	const redirectTarget = getHiddenTabRedirect({
		activeTab,
		visibleTabs,
		hasSelection,
		isProcessLevelWaiting,
		isWaitStateLoading,
	});
	const isActiveTabHidden = redirectTarget !== null;

	useEffect(() => {
		if (redirectTarget !== null) {
			void navigate({
				to:
					redirectTarget === 'details'
						? '/operate-preview/processes/$processInstanceId/details'
						: '/operate-preview/processes/$processInstanceId/variables',
				params: {processInstanceId},
				search: true,
				hash: true,
				replace: true,
			});
		}
	}, [redirectTarget, navigate, processInstanceId]);

	return {visibleTabs, activeTab, isActiveTabHidden};
}

export {useVisibleProcessInstanceTabs};
