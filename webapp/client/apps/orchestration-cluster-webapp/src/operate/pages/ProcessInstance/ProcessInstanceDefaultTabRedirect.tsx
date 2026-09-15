/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {hasProcessLevelWaitState} from '#/operate/shared/utils/waitStates';
import {useProcessInstanceWaitStateStatistics} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {useProcessInstancePage} from '#/operate/pages/ProcessInstance/useProcessInstancePage';
import {
	getDefaultProcessInstanceTab,
	getProcessInstanceTabPath,
} from '#/operate/pages/ProcessInstance/processInstanceSearch';

const ProcessInstanceDefaultTabRedirect: React.FC = () => {
	const navigate = useNavigate();
	const {processInstanceId, processInstance, selection} = useProcessInstancePage();
	const {data: waitStateStatistics, isLoading: isWaitStateLoading} =
		useProcessInstanceWaitStateStatistics(processInstance);

	useEffect(() => {
		if (isWaitStateLoading) {
			return;
		}

		const tab = getDefaultProcessInstanceTab(processInstance, selection, {
			isProcessLevelWaiting: hasProcessLevelWaitState(waitStateStatistics, processInstance.processDefinitionId),
		});

		void navigate({
			to: getProcessInstanceTabPath(tab),
			params: {processInstanceId},
			search: true,
			replace: true,
		});
	}, [navigate, processInstance, processInstanceId, selection, waitStateStatistics, isWaitStateLoading]);

	return null;
};

export {ProcessInstanceDefaultTabRedirect};
