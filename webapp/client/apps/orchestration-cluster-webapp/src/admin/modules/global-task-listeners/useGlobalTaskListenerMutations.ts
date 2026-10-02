/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';
import type {
	CreateGlobalTaskListenerRequestBody,
	GlobalTaskListener,
	UpdateGlobalTaskListenerRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

function useGlobalTaskListenerMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateGlobalTaskListeners = () => queryClient.invalidateQueries({queryKey: ['searchGlobalTaskListeners']});

	const create = useMutation({
		mutationFn: async (globalTaskListener: CreateGlobalTaskListenerRequestBody) => {
			const {error} = await request(endpoints.createGlobalTaskListener(globalTaskListener));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateGlobalTaskListeners();
			toast.success(t('admin.globalTaskListeners.createGlobalTaskListenerSuccess'));
		},
		onError: () => toast.error(t('admin.globalTaskListeners.createGlobalTaskListenerError')),
	});

	const update = useMutation({
		mutationFn: async (globalTaskListener: Pick<GlobalTaskListener, 'id'> & UpdateGlobalTaskListenerRequestBody) => {
			const {error} = await request(endpoints.updateGlobalTaskListener(globalTaskListener));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateGlobalTaskListeners();
			toast.success(t('admin.globalTaskListeners.updateGlobalTaskListenerSuccess'));
		},
		onError: () => toast.error(t('admin.globalTaskListeners.updateGlobalTaskListenerError')),
	});

	const remove = useMutation({
		mutationFn: async (globalTaskListener: Pick<GlobalTaskListener, 'id'>) => {
			const {error} = await request(endpoints.deleteGlobalTaskListener(globalTaskListener));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateGlobalTaskListeners();
			toast.success(t('admin.globalTaskListeners.deleteGlobalTaskListenerSuccess'));
		},
		onError: () => toast.error(t('admin.globalTaskListeners.deleteGlobalTaskListenerError')),
	});

	return {create, update, remove};
}

export {useGlobalTaskListenerMutations};
