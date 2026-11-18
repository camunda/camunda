/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useBlocker} from '@tanstack/react-router';

function useModificationLeaveBlocker({processInstanceId, disabled}: {processInstanceId: string; disabled: boolean}) {
	const isLeavingInstance = useCallback(
		({next}: {next: {params: object}}) =>
			!('processInstanceId' in next.params) || next.params.processInstanceId !== processInstanceId,
		[processInstanceId],
	);

	return useBlocker({
		shouldBlockFn: isLeavingInstance,
		disabled,
		enableBeforeUnload: false,
		withResolver: true,
	});
}

export {useModificationLeaveBlocker};
