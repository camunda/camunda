/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {createFileRoute, redirect} from '@tanstack/react-router';
import {Decisions} from '#/operate/pages/Decisions/Decisions';
import {validateDecisionsSearch} from '#/operate/pages/Decisions/decisionsSearch';
import {loadDecisionsData} from '#/operate/pages/Decisions/loadDecisionsData';
import {notificationsStore} from '#/shared/notifications/notifications.store';

export const Route = createFileRoute('/_carbon/_auth/operate/decisions/')({
	validateSearch: validateDecisionsSearch,
	loaderDeps: ({search: {decisionDefinitionId, decisionDefinitionVersion, tenantId}}) => ({
		decisionDefinitionId,
		decisionDefinitionVersion,
		tenantId,
	}),
	loader: async ({context: {queryClient}, deps: {decisionDefinitionId, decisionDefinitionVersion, tenantId}}) => {
		const isSelectionValid = await loadDecisionsData({
			queryClient,
			decisionDefinitionId,
			decisionDefinitionVersion,
			tenantId,
		});
		if (isSelectionValid) {
			return;
		}

		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.decisions.diagramPanel.decisionNotFoundTitle'),
			isDismissable: true,
		});
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
	},
	component: function DecisionsRoute() {
		return <Decisions {...Route.useSearch()} />;
	},
});
