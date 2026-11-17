/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.11';

function useRefetchOnStatusChange(
	agentInstanceStatus: AgentInstanceStatus,
	isEnabled: boolean,
	refetch: () => unknown,
) {
	const lastAgentInstanceStatus = useRef(agentInstanceStatus);

	useEffect(() => {
		if (!isEnabled) {
			return;
		}
		if (lastAgentInstanceStatus.current !== agentInstanceStatus) {
			void refetch();
		}
		lastAgentInstanceStatus.current = agentInstanceStatus;
	}, [agentInstanceStatus, isEnabled, refetch]);
}

export {useRefetchOnStatusChange};
