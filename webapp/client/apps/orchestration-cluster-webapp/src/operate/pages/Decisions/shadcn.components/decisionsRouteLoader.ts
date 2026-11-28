/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {redirect} from '@tanstack/react-router';
import type {QueryClient} from '@tanstack/react-query';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {loadDecisionsData} from '../loadDecisionsData';

type Params = {
	queryClient: QueryClient;
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
	preload: boolean;
};

async function decisionsRouteLoader({
	queryClient,
	decisionDefinitionId,
	decisionDefinitionVersion,
	tenantId,
	preload,
}: Params) {
	const isSelectionValid = await loadDecisionsData({
		queryClient,
		decisionDefinitionId,
		decisionDefinitionVersion,
		tenantId,
	});
	if (isSelectionValid) {
		return;
	}

	// Preloads on link hover must not show a toast for a navigation that never happens
	if (!preload) {
		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.decisions.diagramPanel.decisionNotFoundTitle'),
			isDismissable: true,
		});
	}
	throw redirect({
		to: '/operate/decisions',
		search: (prev) => ({
			...prev,
			evaluated: prev.evaluated ?? false,
			failed: prev.failed ?? false,
			decisionDefinitionId: undefined,
			decisionDefinitionVersion: undefined,
		}),
		replace: true,
	});
}

export {decisionsRouteLoader};
