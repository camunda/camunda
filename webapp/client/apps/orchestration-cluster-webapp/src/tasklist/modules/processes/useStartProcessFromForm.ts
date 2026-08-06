/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import type {CreateProcessInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {tracking} from '#/shared/tracking';
import {t} from 'i18next';

type StartProcessFromFormVariables = {
	processDefinitionKey: string;
	tenantId: string | undefined;
	variables: Record<string, unknown>;
};

function useStartProcessFromForm() {
	return useMutation({
		mutationFn: async (variables: StartProcessFromFormVariables): Promise<CreateProcessInstanceResponseBody> => {
			const {response, error} = await request(endpoints.createProcessInstance(variables));

			if (error !== null) {
				throw error;
			}

			return response.json();
		},
		onSuccess: () => {
			tracking.track({eventName: 'tasklist:process-started'});
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('tasklist.processesStartProcessNotificationSuccess'),
				isDismissable: true,
			});
		},
	});
}

export {useStartProcessFromForm};
