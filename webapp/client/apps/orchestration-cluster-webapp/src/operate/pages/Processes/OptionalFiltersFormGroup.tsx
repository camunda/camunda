/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {FieldValidator} from 'final-form';
import {Field, useForm} from 'react-final-form';
import {Checkbox, IconButton, Stack} from '@carbon/react';
import {Close} from '@carbon/react/icons';
import {tracking} from '#/shared/tracking';
import {OptionalFiltersMenu} from '#/operate/shared/OptionalFiltersMenu/OptionalFiltersMenu';
import {DateRangeField} from '#/operate/shared/DateRangeField/DateRangeField';
import {AdvancedStringFilter} from '#/operate/shared/AdvancedStringFilter/AdvancedStringFilter';
import {TextInputField} from '#/operate/shared/TextInputField/TextInputField';
import {TextAreaField} from '#/operate/shared/TextAreaField/TextAreaField';
import {FieldContainer, ButtonContainer} from '#/operate/shared/FiltersPanel/styled';
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

// The legacy 'variable' optional filter is deliberately omitted here — it stays hidden until
// #57671 (conditions logic) and #57672 (modal) land.
type OptionalFilter =
	| 'processInstanceKey'
	| 'parentProcessInstanceKey'
	| 'businessId'
	| 'batchOperationKey'
	| 'errorMessage'
	| 'hasRetriesLeft'
	| 'startDateRange'
	| 'endDateRange';

type OptionalFilterValues = {
	processInstanceKey?: string;
	parentProcessInstanceKey?: string;
	businessId?: string;
	batchOperationKey?: string;
	errorMessage?: string;
	hasRetriesLeft?: boolean;
	startDateFrom?: string;
	startDateTo?: string;
	endDateFrom?: string;
	endDateTo?: string;
};

const optionalFilters: OptionalFilter[] = [
	'processInstanceKey',
	'businessId',
	'batchOperationKey',
	'parentProcessInstanceKey',
	'errorMessage',
	'hasRetriesLeft',
	'startDateRange',
	'endDateRange',
];

const OPTIONAL_FILTER_FIELDS: Record<
	OptionalFilter,
	{
		labelKey: string;
		placeholderKey?: string;
		type?: 'multiline' | 'text' | 'checkbox';
		rows?: number;
		validate?: FieldValidator<string | undefined>;
		keys: (keyof OptionalFilterValues)[];
	}
> = {
	processInstanceKey: {
		keys: ['processInstanceKey'],
		labelKey: 'operate.processes.filters.processInstanceKey',
		type: 'multiline',
		placeholderKey: 'operate.processes.filters.processInstanceKeyPlaceholder',
		rows: 1,
		validate: mergeValidators(validateIdsCharacters, validateIdsLength, validatesIdsComplete),
	},
	businessId: {
		keys: ['businessId'],
		labelKey: 'operate.processes.filters.businessId',
	},
	batchOperationKey: {
		keys: ['batchOperationKey'],
		labelKey: 'operate.processes.filters.batchOperationKey',
		type: 'text',
		validate: mergeValidators(validateBatchOperationKeyCharacters, validateBatchOperationKeyComplete),
	},
	parentProcessInstanceKey: {
		keys: ['parentProcessInstanceKey'],
		labelKey: 'operate.processes.filters.parentProcessInstanceKey',
		type: 'text',
		validate: mergeValidators(
			validateParentInstanceIdComplete,
			validateParentInstanceIdNotTooLong,
			validateParentInstanceIdCharacters,
		),
	},
	errorMessage: {
		keys: ['errorMessage'],
		labelKey: 'operate.processes.filters.errorMessage',
		type: 'text',
	},
	hasRetriesLeft: {
		keys: ['hasRetriesLeft'],
		labelKey: 'operate.processes.filters.hasRetriesLeft',
		type: 'checkbox',
	},
	startDateRange: {
		keys: ['startDateFrom', 'startDateTo'],
		labelKey: 'operate.processes.filters.startDateRange',
	},
	endDateRange: {
		keys: ['endDateFrom', 'endDateTo'],
		labelKey: 'operate.processes.filters.endDateRange',
	},
};

type Props = {
	filters: OptionalFilterValues;
	visibleFilters: OptionalFilter[];
	onVisibleFilterChange: React.Dispatch<React.SetStateAction<OptionalFilter[]>>;
};

const OptionalFiltersFormGroup: React.FC<Props> = ({filters, visibleFilters, onVisibleFilterChange}) => {
	const {t} = useTranslation();
	const form = useForm();

	useEffect(() => {
		const activeFilters: OptionalFilter[] = [
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
			...(filters.startDateFrom !== undefined && filters.startDateTo !== undefined
				? (['startDateRange'] as const)
				: []),
			...(filters.endDateFrom !== undefined && filters.endDateTo !== undefined ? (['endDateRange'] as const) : []),
		];

		onVisibleFilterChange((currentVisibleFilters) => {
			const nextVisibleFilters = Array.from(new Set([...currentVisibleFilters, ...activeFilters]));
			return nextVisibleFilters.length === currentVisibleFilters.length ? currentVisibleFilters : nextVisibleFilters;
		});
	}, [filters, onVisibleFilterChange]);

	const [isStartDateRangeModalOpen, setIsStartDateRangeModalOpen] = useState<boolean>(false);
	const [isEndDateRangeModalOpen, setIsEndDateRangeModalOpen] = useState<boolean>(false);

	return (
		<Stack gap={8}>
			<OptionalFiltersMenu<OptionalFilter>
				visibleFilters={visibleFilters}
				optionalFilters={optionalFilters.map((id) => ({
					id,
					label: t(OPTIONAL_FILTER_FIELDS[id].labelKey),
				}))}
				onFilterSelect={(filter) => {
					onVisibleFilterChange((currentVisibleFilters) => Array.from(new Set([...currentVisibleFilters, filter])));
					tracking.track({
						eventName: 'operate:optional-filter-selected',
						filterName: filter,
					});
					if (filter === 'startDateRange') {
						setIsStartDateRangeModalOpen(true);
					}
					if (filter === 'endDateRange') {
						setIsEndDateRangeModalOpen(true);
					}
				}}
			/>
			<Stack gap={5}>
				{visibleFilters.map((filter) => (
					<FieldContainer key={filter}>
						{(() => {
							switch (filter) {
								case 'businessId':
									return (
										<AdvancedStringFilter
											name={filter}
											label={t(OPTIONAL_FILTER_FIELDS[filter].labelKey)}
											selectableOperators={['$eq', '$like', '$in']}
										/>
									);
								case 'startDateRange':
									return (
										<DateRangeField
											isModalOpen={isStartDateRangeModalOpen}
											onModalClose={() => setIsStartDateRangeModalOpen(false)}
											onClick={() => setIsStartDateRangeModalOpen(true)}
											filterName={filter}
											popoverTitle={t('operate.processes.filters.startDatePopoverTitle')}
											label={t(OPTIONAL_FILTER_FIELDS[filter].labelKey)}
											fromDateTimeKey="startDateFrom"
											toDateTimeKey="startDateTo"
										/>
									);
								case 'endDateRange':
									return (
										<DateRangeField
											isModalOpen={isEndDateRangeModalOpen}
											onModalClose={() => setIsEndDateRangeModalOpen(false)}
											onClick={() => setIsEndDateRangeModalOpen(true)}
											filterName={filter}
											popoverTitle={t('operate.processes.filters.endDatePopoverTitle')}
											label={t(OPTIONAL_FILTER_FIELDS[filter].labelKey)}
											fromDateTimeKey="endDateFrom"
											toDateTimeKey="endDateTo"
										/>
									);
								default:
									return (
										<Field
											name={filter}
											validate={OPTIONAL_FILTER_FIELDS[filter].validate}
											type={OPTIONAL_FILTER_FIELDS[filter].type}
										>
											{({input}) => {
												const field = OPTIONAL_FILTER_FIELDS[filter];

												if (field.type === 'text') {
													return (
														<TextInputField
															{...input}
															id={filter}
															size="sm"
															labelText={t(field.labelKey)}
															placeholder={field.placeholderKey ? t(field.placeholderKey) : undefined}
														/>
													);
												}
												if (field.type === 'multiline') {
													return (
														<TextAreaField
															{...input}
															id={filter}
															labelText={t(field.labelKey)}
															placeholder={field.placeholderKey ? t(field.placeholderKey) : undefined}
															rows={field.rows}
														/>
													);
												}
												if (field.type === 'checkbox') {
													return <Checkbox {...input} id={filter} labelText={t(field.labelKey)} />;
												}
												return null;
											}}
										</Field>
									);
							}
						})()}
						<ButtonContainer>
							<IconButton
								kind="ghost"
								label={t('operate.processes.filters.removeFilter', {
									label: t(OPTIONAL_FILTER_FIELDS[filter].labelKey),
								})}
								align="top-end"
								size="sm"
								onClick={() => {
									onVisibleFilterChange((currentVisibleFilters) =>
										currentVisibleFilters.filter((visibleFilter) => visibleFilter !== filter),
									);

									OPTIONAL_FILTER_FIELDS[filter].keys.forEach((key) => {
										form.change(key, undefined);
									});

									form.submit();
								}}
							>
								<Close />
							</IconButton>
						</ButtonContainer>
					</FieldContainer>
				))}
			</Stack>
		</Stack>
	);
};

export {OptionalFiltersFormGroup};
export type {OptionalFilter, OptionalFilterValues};
