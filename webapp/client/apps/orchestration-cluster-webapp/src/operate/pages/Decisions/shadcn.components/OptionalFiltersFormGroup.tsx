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
import {IconButton, Label} from '@camunda/design-system';
import {X} from '@camunda/design-system/icons';
import {OptionalFiltersMenu} from '#/operate/shared/OptionalFiltersMenu/shadcn.components/OptionalFiltersMenu';
import {DateRangeField} from '#/operate/shared/DateRangeField/shadcn.components/DateRangeField';
import {AdvancedStringFilter} from '#/operate/shared/AdvancedStringFilter/shadcn.components/AdvancedStringFilter';
import {TextInputField} from '#/operate/shared/TextInputField/shadcn.components/TextInputField';
import {TextAreaField} from '#/operate/shared/TextAreaField/shadcn.components/TextAreaField';
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
		<div className="flex flex-col gap-8">
			<OptionalFiltersMenu<OptionalFilter>
				visibleFilters={visibleFilters}
				optionalFilters={optionalFilters.map((id) => ({
					id,
					label: t(OPTIONAL_FILTER_FIELDS[id].labelKey),
				}))}
				onFilterSelect={(filter) => {
					onVisibleFilterChange((currentVisibleFilters) => Array.from(new Set([...currentVisibleFilters, filter])));
					if (filter === 'evaluationDateRange') {
						setIsDateRangeModalOpen(true);
					}
				}}
			/>
			<div className="flex flex-col gap-5">
				{visibleFilters.map((filter) => {
					const field = OPTIONAL_FILTER_FIELDS[filter];
					const label = t(field.labelKey);

					return (
						<div key={filter} className="flex items-end gap-1">
							<div className="min-w-0 flex-1">
								{(() => {
									switch (filter) {
										case 'businessId':
											return (
												<AdvancedStringFilter
													name={filter}
													label={label}
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
													label={label}
													fromDateTimeKey="evaluationDateFrom"
													toDateTimeKey="evaluationDateTo"
												/>
											);
										default:
											return (
												<Field name={filter} validate={field.validate}>
													{({input}) => (
														<div className="flex flex-col gap-1.5">
															<Label htmlFor={filter}>{label}</Label>
															{field.type === 'multiline' ? (
																<TextAreaField
																	{...input}
																	id={filter}
																	placeholder={field.placeholderKey ? t(field.placeholderKey) : undefined}
																	rows={field.rows}
																	autoFocus
																/>
															) : (
																<TextInputField
																	{...input}
																	id={filter}
																	placeholder={field.placeholderKey ? t(field.placeholderKey) : undefined}
																	autoFocus
																/>
															)}
														</div>
													)}
												</Field>
											);
									}
								})()}
							</div>
							<IconButton
								variant="ghost"
								size="sm"
								icon={X}
								label={t('operate.decisions.filters.removeFilter', {label})}
								tooltipSide="top"
								onClick={() => {
									onVisibleFilterChange((currentVisibleFilters) =>
										currentVisibleFilters.filter((visibleFilter) => visibleFilter !== filter),
									);

									field.keys.forEach((key) => {
										form.change(key, undefined);
									});

									form.submit();
								}}
							/>
						</div>
					);
				})}
			</div>
		</div>
	);
};

export {OptionalFiltersFormGroup};
