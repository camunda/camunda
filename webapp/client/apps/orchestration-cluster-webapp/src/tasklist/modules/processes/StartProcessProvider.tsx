/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from '@tanstack/react-router';
import {useActorRef} from '@xstate/react';
import {StartProcessContext} from './startProcessContext';
import {startProcessMachine} from './startProcessMachine';

const StartProcessProvider: React.FC<{children: React.ReactNode}> = ({children}) => {
	const navigate = useNavigate();
	const actorRef = useActorRef(startProcessMachine, {input: {navigate}});

	return <StartProcessContext.Provider value={actorRef}>{children}</StartProcessContext.Provider>;
};

export {StartProcessProvider};
