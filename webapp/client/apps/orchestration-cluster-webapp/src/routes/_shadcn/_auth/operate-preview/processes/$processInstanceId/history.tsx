/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Navigate} from '@tanstack/react-router';
import {InstanceHistory} from '#/operate/pages/ProcessInstance/shadcn.components/InstanceHistory';
import {isWidthAboveBreakpoint, useMatchMedia} from '#/operate/shared/useMatchMedia';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/processes/$processInstanceId/history')({
	component: function ProcessInstanceHistoryTab() {
		const {processInstanceId} = Route.useParams();
		const isDesktop = useMatchMedia(isWidthAboveBreakpoint('lg'));

		return isDesktop ? (
			<Navigate
				to="/operate-preview/processes/$processInstanceId/variables"
				params={{processInstanceId}}
				search
				hash
				replace
			/>
		) : (
			<InstanceHistory showHeader={false} />
		);
	},
});
