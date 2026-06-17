/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {runningInstancesCountQuery} from '#/operate/pages/Dashboard/useRunningInstancesCount';
import {Dashboard} from '#/operate/pages/Dashboard/Dashboard';

export const Route = createFileRoute('/_auth/operate/')({
	loader: async ({context: {queryClient}}) => {
		await queryClient.ensureQueryData(runningInstancesCountQuery());
	},
	component: Dashboard,
});
