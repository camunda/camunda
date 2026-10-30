/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useMatchRoute} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Heading} from '@camunda/design-system';
import {
	OptionalFiltersFormGroup as SharedOptionalFiltersFormGroup,
	type OptionalFilterDefinition,
} from '#/operate/shared/OptionalFiltersFormGroup/shadcn.components/OptionalFiltersFormGroup';
import {DateTimeRangeField} from '#/operate/shared/DateTimeRangeField/shadcn.components/DateTimeRangeField';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {
	validateIdsCharacters,
	validateIdsLength,
	validatesIdsComplete,
	validateParentInstanceIdCharacters,
	validateParentInstanceIdComplete,
	validateParentInstanceIdNotTooLong,
	validateBatchOperationKeyCharacters,
	validateBatchOperationKeyComplete,
} from '#/operate/shared/utils/validators';
import {setVariableConditions, useVariableConditions} from '../VariablesFilter/variableFilterStore';
import type {OptionalFilter, OptionalFilterValues} from './optionalFilters';
import {VariablesFilter} from './VariablesFilter/VariablesFilter';

type Props = {
	filters: OptionalFilterValues;
	visibleFilters: OptionalFilter[];
	onVisibleFilterChange: React.Dispatch<React.SetStateAction<OptionalFilter[]>>;
	onFilterClear?: (keys: string[]) => void;
};

const OptionalFiltersFormGroup: React.FC<Props> = ({filters, visibleFilters, onVisibleFilterChange, onFilterClear}) => {
	const {t} = useTranslation();
	const hasActiveVariableFilters = useVariableConditions().length > 0;
	const isOnVariablesRoute = Boolean(useMatchRoute()({to: '/operate-preview/processes/filters/variables'}));

	const definitions = useMemo<OptionalFilterDefinition<OptionalFilter>[]>(() => {
		const dateRangeDefinition = (
			id: 'startDateRange' | 'endDateRange',
			label: string,
			fromKey: string,
			toKey: string,
		): OptionalFilterDefinition<OptionalFilter> => ({
			id,
			keys: [fromKey, toKey],
			label,
			type: 'custom',
			render: (removeButton) => (
				<div
					id={id}
					tabIndex={-1}
					role="group"
					aria-labelledby={`optional-filter-${id}-label`}
					className="flex flex-col gap-1.5"
				>
					<div className="flex items-center justify-between">
						<Heading as="h3" variant="heading-xs" id={`optional-filter-${id}-label`}>
							{label}
						</Heading>
						{removeButton}
					</div>
					<DateTimeRangeField idPrefix={`optional-filter-${id}`} fromKey={fromKey} toKey={toKey} />
				</div>
			),
		});

		return [
			{
				id: 'processInstanceKey',
				keys: ['processInstanceKey'],
				label: t('operate.processes.filters.processInstanceKey'),
				type: 'multiline',
				placeholder: t('operate.processes.filters.processInstanceKeyPlaceholder'),
				rows: 1,
				validate: mergeValidators(validateIdsCharacters, validateIdsLength, validatesIdsComplete),
			},
			{
				id: 'businessId',
				keys: ['businessId'],
				label: t('operate.processes.filters.businessId'),
				type: 'advancedString',
				selectableOperators: ['$eq', '$like', '$in'],
			},
			{
				id: 'batchOperationKey',
				keys: ['batchOperationKey'],
				label: t('operate.processes.filters.batchOperationKey'),
				type: 'text',
				validate: mergeValidators(validateBatchOperationKeyCharacters, validateBatchOperationKeyComplete),
			},
			{
				id: 'parentProcessInstanceKey',
				keys: ['parentProcessInstanceKey'],
				label: t('operate.processes.filters.parentProcessInstanceKey'),
				type: 'text',
				validate: mergeValidators(
					validateParentInstanceIdComplete,
					validateParentInstanceIdNotTooLong,
					validateParentInstanceIdCharacters,
				),
			},
			{
				id: 'errorMessage',
				keys: ['errorMessage', 'incidentErrorHashCode'],
				label: t('operate.processes.filters.errorMessage'),
				type: 'text',
			},
			{
				id: 'hasRetriesLeft',
				keys: ['hasRetriesLeft'],
				label: t('operate.processes.filters.hasRetriesLeft'),
				type: 'checkbox',
			},
			{
				id: 'variable',
				keys: ['variableName', 'variableValues'],
				label: t('operate.processes.variableFilter.title'),
				type: 'custom',
				onRemove: () => setVariableConditions([]),
				render: (removeButton) => (
					<div
						id="variable"
						tabIndex={-1}
						role="group"
						aria-labelledby="optional-filter-variable-label"
						className="flex flex-col gap-1.5"
					>
						<div className="flex items-center justify-between">
							<Heading as="h3" variant="heading-xs" id="optional-filter-variable-label">
								{t('operate.processes.variableFilter.title')}
							</Heading>
							{removeButton}
						</div>
						<VariablesFilter />
					</div>
				),
			},
			dateRangeDefinition(
				'startDateRange',
				t('operate.processes.filters.startDateRange'),
				'startDateFrom',
				'startDateTo',
			),
			dateRangeDefinition('endDateRange', t('operate.processes.filters.endDateRange'), 'endDateFrom', 'endDateTo'),
		];
	}, [t]);

	const activeFilters = useMemo<OptionalFilter[]>(
		() => [
			...(hasActiveVariableFilters || isOnVariablesRoute ? (['variable'] as const) : []),
			...(
				[
					'processInstanceKey',
					'parentProcessInstanceKey',
					'businessId',
					'batchOperationKey',
					'errorMessage',
					'hasRetriesLeft',
				] as const
			).filter((filter) => filters[filter] !== undefined),
			...(filters.startDateFrom !== undefined || filters.startDateTo !== undefined
				? (['startDateRange'] as const)
				: []),
			...(filters.endDateFrom !== undefined || filters.endDateTo !== undefined ? (['endDateRange'] as const) : []),
		],
		[filters, hasActiveVariableFilters, isOnVariablesRoute],
	);

	return (
		<SharedOptionalFiltersFormGroup
			definitions={definitions}
			activeFilters={activeFilters}
			visibleFilters={visibleFilters}
			onVisibleFilterChange={onVisibleFilterChange}
			onFilterClear={onFilterClear}
			getRemoveFilterLabel={(label) => t('operate.processes.filters.removeFilter', {label})}
		/>
	);
};

export {OptionalFiltersFormGroup};
