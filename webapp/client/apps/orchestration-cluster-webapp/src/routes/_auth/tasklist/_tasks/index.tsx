/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, useLoaderData} from '@tanstack/react-router';

import {NoTaskSelectedPage} from '#/tasklist/pages/NoTaskSelectedPage';

export const Route = createFileRoute('/_auth/tasklist/_tasks/')({
	component: function NoTaskSelectedRoute() {
		const data = useLoaderData({from: '/_auth/tasklist/_tasks'});
		const hasNoTasks = data.pages[0]?.items.length === 0;

		return <NoTaskSelectedPage hasNoTasks={hasNoTasks} />;
	},
});
