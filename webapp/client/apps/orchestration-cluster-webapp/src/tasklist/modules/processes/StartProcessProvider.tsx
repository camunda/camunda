/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ReactNode} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useActorRef} from '@xstate/react';
import {StartProcessContext} from './startProcessContext';
import {startProcessMachine, type ProcessesRoute, type TaskRoute} from './startProcessMachine';

type Props = {
	children: ReactNode;
	// Default to the Carbon routes so the existing Carbon call site (which
	// passes neither) is unaffected; the shadcn route passes its own tree.
	processesRoute?: ProcessesRoute;
	taskRoute?: TaskRoute;
};

function StartProcessProvider({
	children,
	processesRoute = '/tasklist/processes',
	taskRoute = '/tasklist/$userTaskKey',
}: Props) {
	const navigate = useNavigate();
	const actorRef = useActorRef(startProcessMachine, {input: {navigate, processesRoute, taskRoute}});

	return <StartProcessContext.Provider value={actorRef}>{children}</StartProcessContext.Provider>;
}

export {StartProcessProvider};
