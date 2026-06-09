/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery} from '@tanstack/react-query';
import type {MessageSubscription} from '@camunda/camunda-api-zod-schemas/8.10';
import {queries} from '#/shared/http/queries';

type Props = {
	messageSubscriptions: MessageSubscription[];
};

const McpProcessesOverviewPage: React.FC<Props> = ({messageSubscriptions}) => {
	return (
		<main>
			<h1>MCP Processes Overview</h1>
			<p>{messageSubscriptions.length} message subscription(s) on partition 1</p>
		</main>
	);
};

const McpProcessesOverview: React.FC = () => {
	const {data} = useSuspenseQuery(queries.getMcpProcessMessageSubscriptions());

	return <McpProcessesOverviewPage messageSubscriptions={data.items} />;
};

export {McpProcessesOverviewPage, McpProcessesOverview};
