/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {
	OptionalFiltersFormGroup as SharedOptionalFiltersFormGroup,
	type OptionalFilterDefinition,
} from '#/operate/shared/OptionalFiltersFormGroup/shadcn.components/OptionalFiltersFormGroup';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {
	validateIdsCharacters,
	validateIdsLength,
	validatesIdsComplete,
	validateParentInstanceIdCharacters,
	validateParentInstanceIdComplete,
	validateParentInstanceIdNotTooLong,
} from '#/operate/shared/utils/validators';
import type {OptionalFilter, OptionalFilterValues} from '../OptionalFiltersFormGroup';

type Props = {
	filters: OptionalFilterValues;
	visibleFilters: OptionalFilter[];
	onVisibleFilterChange: React.Dispatch<React.SetStateAction<OptionalFilter[]>>;
};

const OptionalFiltersFormGroup: React.FC<Props> = ({filters, visibleFilters, onVisibleFilterChange}) => {
	const {t} = useTranslation();

	const definitions = useMemo<OptionalFilterDefinition<OptionalFilter>[]>(
		() => [
			{
				id: 'decisionEvaluationInstanceKey',
				keys: ['decisionEvaluationInstanceKey'],
				label: t('operate.decisions.filters.decisionInstanceKey'),
				type: 'multiline',
				placeholder: t('operate.decisions.filters.decisionInstanceKeyPlaceholder'),
				rows: 1,
				validate: mergeValidators(validateIdsCharacters, validateIdsLength, validatesIdsComplete),
			},
			{
				id: 'processInstanceKey',
				keys: ['processInstanceKey'],
				label: t('operate.decisions.filters.processInstanceKey'),
				type: 'text',
				validate: mergeValidators(
					validateParentInstanceIdComplete,
					validateParentInstanceIdNotTooLong,
					validateParentInstanceIdCharacters,
				),
			},
			{
				id: 'businessId',
				keys: ['businessId'],
				label: t('operate.decisions.filters.businessId'),
				type: 'advancedString',
				selectableOperators: ['$eq', '$like', '$in'],
			},
			{
				id: 'evaluationDateRange',
				keys: ['evaluationDateFrom', 'evaluationDateTo'],
				label: t('operate.decisions.filters.evaluationDateRange'),
				type: 'dateRange',
				popoverTitle: t('operate.decisions.filters.evaluationDatePopoverTitle'),
				fromKey: 'evaluationDateFrom',
				toKey: 'evaluationDateTo',
			},
		],
		[t],
	);

	const activeFilters = useMemo<OptionalFilter[]>(
		() => [
			...(['decisionEvaluationInstanceKey', 'processInstanceKey', 'businessId'] as const).filter(
				(filter) => filters[filter] !== undefined,
			),
			...(filters.evaluationDateFrom !== undefined && filters.evaluationDateTo !== undefined
				? (['evaluationDateRange'] as const)
				: []),
		],
		[filters],
	);

	return (
		<SharedOptionalFiltersFormGroup
			definitions={definitions}
			activeFilters={activeFilters}
			visibleFilters={visibleFilters}
			onVisibleFilterChange={onVisibleFilterChange}
			getRemoveFilterLabel={(label) => t('operate.decisions.filters.removeFilter', {label})}
		/>
	);
};

export {OptionalFiltersFormGroup};
