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
import {IconButton, Stack} from '@carbon/react';
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
} from '#/operate/shared/utils/validators';

type OptionalFilter = 'decisionEvaluationInstanceKey' | 'processInstanceKey' | 'businessId' | 'evaluationDateRange';

type OptionalFilterValues = {
	decisionEvaluationInstanceKey?: string;
	processInstanceKey?: string;
	businessId?: string;
	evaluationDateFrom?: string;
	evaluationDateTo?: string;
};

const optionalFilters: OptionalFilter[] = [
	'decisionEvaluationInstanceKey',
	'processInstanceKey',
	'businessId',
	'evaluationDateRange',
];

const OPTIONAL_FILTER_FIELDS: Record<
	OptionalFilter,
	{
		labelKey: string;
		placeholderKey?: string;
		type?: 'multiline' | 'text';
		rows?: number;
		validate?: FieldValidator<string | undefined>;
		keys: (keyof OptionalFilterValues)[];
	}
> = {
	decisionEvaluationInstanceKey: {
		keys: ['decisionEvaluationInstanceKey'],
		labelKey: 'operate.decisions.filters.decisionInstanceKey',
		type: 'multiline',
		placeholderKey: 'operate.decisions.filters.decisionInstanceKeyPlaceholder',
		rows: 1,
		validate: mergeValidators(validateIdsCharacters, validateIdsLength, validatesIdsComplete),
	},
	processInstanceKey: {
		keys: ['processInstanceKey'],
		labelKey: 'operate.decisions.filters.processInstanceKey',
		type: 'text',
		validate: mergeValidators(
			validateParentInstanceIdComplete,
			validateParentInstanceIdNotTooLong,
			validateParentInstanceIdCharacters,
		),
	},
	businessId: {
		keys: ['businessId'],
		labelKey: 'operate.decisions.filters.businessId',
	},
	evaluationDateRange: {
		keys: ['evaluationDateFrom', 'evaluationDateTo'],
		labelKey: 'operate.decisions.filters.evaluationDateRange',
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
			...(['decisionEvaluationInstanceKey', 'processInstanceKey', 'businessId'] as const).filter(
				(filter) => filters[filter] !== undefined,
			),
			...(filters.evaluationDateFrom !== undefined && filters.evaluationDateTo !== undefined
				? (['evaluationDateRange'] as const)
				: []),
		];

		onVisibleFilterChange((currentVisibleFilters) => {
			const nextVisibleFilters = Array.from(new Set([...currentVisibleFilters, ...activeFilters]));
			return nextVisibleFilters.length === currentVisibleFilters.length ? currentVisibleFilters : nextVisibleFilters;
		});
	}, [filters, onVisibleFilterChange]);

	const [isDateRangeModalOpen, setIsDateRangeModalOpen] = useState<boolean>(false);

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
					if (filter === 'evaluationDateRange') {
						setIsDateRangeModalOpen(true);
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
								case 'evaluationDateRange':
									return (
										<DateRangeField
											isModalOpen={isDateRangeModalOpen}
											onModalClose={() => setIsDateRangeModalOpen(false)}
											onClick={() => setIsDateRangeModalOpen(true)}
											filterName={filter}
											popoverTitle={t('operate.decisions.filters.evaluationDatePopoverTitle')}
											label={t(OPTIONAL_FILTER_FIELDS[filter].labelKey)}
											fromDateTimeKey="evaluationDateFrom"
											toDateTimeKey="evaluationDateTo"
										/>
									);
								default:
									return (
										<Field name={filter} validate={OPTIONAL_FILTER_FIELDS[filter].validate}>
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
															autoFocus
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
															autoFocus
														/>
													);
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
								label={t('operate.decisions.filters.removeFilter', {label: t(OPTIONAL_FILTER_FIELDS[filter].labelKey)})}
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
