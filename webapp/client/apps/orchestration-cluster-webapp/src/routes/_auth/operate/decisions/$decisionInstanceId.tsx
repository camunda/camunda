/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {DecisionInstance} from '#/operate/pages/DecisionInstance/DecisionInstance';

export const Route = createFileRoute('/_auth/operate/decisions/$decisionInstanceId')({
	component: function DecisionInstanceRoute() {
		const {decisionInstanceId} = Route.useParams();
		return <DecisionInstance decisionInstanceId={decisionInstanceId} />;
	},
});
