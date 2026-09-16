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
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {useProcessInstanceWaitStateStatistics} from './processInstance.queries';
import {useProcessInstancePage} from './useProcessInstancePage';
import {getDefaultProcessInstanceTab, getProcessInstanceTabPath} from './processInstanceSearch';

const ProcessInstanceDefaultTabRedirect: React.FC = () => {
	const navigate = useNavigate();
	const {processInstanceId, processInstance, selection} = useProcessInstancePage();
	const {data, isPending, isFetching, isError} = useProcessInstanceWaitStateStatistics(processInstance);
	const isEnabled = isInstanceRunning(processInstance);

	useEffect(() => {
		if (isEnabled && (isPending || isFetching || isError)) {
			return;
		}

		const tab = getDefaultProcessInstanceTab(processInstance, selection, {
			isProcessLevelWaiting: hasProcessLevelWaitState(data, processInstance.processDefinitionId),
		});

		void navigate({
			to: getProcessInstanceTabPath(tab),
			params: {processInstanceId},
			search: true,
			replace: true,
		});
	}, [navigate, processInstance, processInstanceId, selection, data, isPending, isFetching, isError, isEnabled]);

	return null;
};

export {ProcessInstanceDefaultTabRedirect};
