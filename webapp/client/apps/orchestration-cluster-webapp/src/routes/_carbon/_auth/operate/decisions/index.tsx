/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {createFileRoute, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {Decisions} from '#/operate/pages/Decisions/Decisions';
import {loadDecisionsData} from '#/operate/pages/Decisions/loadDecisionsData';
import {notificationsStore} from '#/shared/notifications/notifications.store';

const decisionsSearchSchema = z.object({
	decisionDefinitionId: z.string().optional(),
	decisionDefinitionVersion: z.number().int().positive().optional(),
	tenantId: z.coerce.string().optional(),
	evaluated: z.boolean().default(true),
	failed: z.boolean().default(true),
	// coerce: small (safe-range) numeric-looking keys still arrive typed as a JS number from the
	// router's search parser (main.tsx's parseSearchValueSafe only keeps precision-losing large
	// keys as strings, it doesn't change the type of small ones) — normalize to string either way
	decisionEvaluationInstanceKey: z.coerce.string().optional(),
	processInstanceKey: z.coerce.string().optional(),
	businessId: z.string().optional(),
	evaluationDateFrom: z.string().optional(),
	evaluationDateTo: z.string().optional(),
	sort: z.string().optional(),
});

export const Route = createFileRoute('/_carbon/_auth/operate/decisions/')({
	validateSearch: decisionsSearchSchema,
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
