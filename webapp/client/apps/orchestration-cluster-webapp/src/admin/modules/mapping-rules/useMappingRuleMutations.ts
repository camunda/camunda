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
import type {MappingRule, UpdateMappingRuleRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {isDuplicateMappingRuleIdError} from './isDuplicateMappingRuleIdError';

function useMappingRuleMutations() {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateMappingRules = () => queryClient.invalidateQueries({queryKey: ['queryMappingRules']});

	const create = useMutation({
		mutationFn: async (mappingRule: MappingRule) => {
			const {error} = await request(endpoints.createMappingRule(mappingRule));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateMappingRules();
			toast.success(t('admin.mappingRules.createMappingRuleSuccess'));
		},
		onError: (error) => {
			// A duplicate ID is corrected inline on the mapping rule ID field instead of a toast.
			if (!isDuplicateMappingRuleIdError(error)) {
				toast.error(t('admin.mappingRules.createMappingRuleError'));
			}
		},
	});

	const update = useMutation({
		mutationFn: async (mappingRule: Pick<MappingRule, 'mappingRuleId'> & UpdateMappingRuleRequestBody) => {
			const {error} = await request(endpoints.updateMappingRule(mappingRule));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateMappingRules();
			toast.success(t('admin.mappingRules.updateMappingRuleSuccess'));
		},
		onError: () => toast.error(t('admin.mappingRules.updateMappingRuleError')),
	});

	const remove = useMutation({
		mutationFn: async (mappingRule: Pick<MappingRule, 'mappingRuleId'>) => {
			const {error} = await request(endpoints.deleteMappingRule(mappingRule));
			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSuccess: () => {
			invalidateMappingRules();
			toast.success(t('admin.mappingRules.deleteMappingRuleSuccess'));
		},
		onError: () => toast.error(t('admin.mappingRules.deleteMappingRuleError')),
	});

	return {create, update, remove};
}

export {useMappingRuleMutations};
