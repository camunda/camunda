/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Copy} from '@carbon/react/icons';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useVariableScope} from './useVariableScope';
import {variablesQuery} from './variables.queries';

const variablesAsJSON = (variables: Variable[]) => {
	try {
		return JSON.stringify(Object.fromEntries(variables.map(({name, value}) => [name, JSON.parse(value)])));
	} catch {
		return '{}';
	}
};

const CopyVariablesButton: React.FC<{scopeKey: string}> = ({scopeKey}) => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {canQuery} = useVariableScope();
	const {data, hasNextPage} = useInfiniteQuery(
		variablesQuery({processInstanceKey: processInstanceId, scopeKey: canQuery ? scopeKey : null}),
	);
	const pages = data?.pages ?? [];
	const variables = pages.flatMap(({items}) => items);
	const firstPage = pages[0];
	const isPaginated =
		pages.length >= 2 ||
		hasNextPage ||
		firstPage?.page.hasMoreTotalItems === true ||
		variables.length < (firstPage?.page.totalItems ?? 0);
	const isTruncated = variables.some((variable) => variable.isTruncated);
	const disabledReason = isPaginated
		? t('operate.processInstance.variables.copyPaginated')
		: isTruncated
			? t('operate.processInstance.variables.copyTruncated')
			: undefined;

	return (
		<Button
			kind="ghost"
			size="md"
			disabled={isPaginated || isTruncated || variables.length === 0}
			title={disabledReason ?? t('operate.processInstance.variables.copyTitle')}
			onClick={() => {
				void navigator.clipboard.writeText(variablesAsJSON(variables));
			}}
			renderIcon={Copy}
		>
			{t('operate.processInstance.variables.copy')}
		</Button>
	);
};

export {CopyVariablesButton};
