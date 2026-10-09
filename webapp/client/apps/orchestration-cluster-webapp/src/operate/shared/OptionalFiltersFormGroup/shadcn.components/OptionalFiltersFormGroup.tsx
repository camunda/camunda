/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import type {FieldValidator} from 'final-form';
import {Field, useForm} from 'react-final-form';
import {Checkbox, IconButton, Label} from '@camunda/design-system';
import {X} from '@camunda/design-system/icons';
import {OptionalFiltersMenu} from '#/operate/shared/OptionalFiltersMenu/shadcn.components/OptionalFiltersMenu';
import {DateRangeField} from '#/operate/shared/DateRangeField/shadcn.components/DateRangeField';
import {AdvancedStringFilter} from '#/operate/shared/AdvancedStringFilter/shadcn.components/AdvancedStringFilter';
import {TextInputField} from '#/operate/shared/TextInputField/shadcn.components/TextInputField';
import {TextAreaField} from '#/operate/shared/TextAreaField/shadcn.components/TextAreaField';
import type {AdvancedStringFilterOperator} from '#/operate/shared/utils/advancedStringFilter';

type BaseDefinition<Id extends string> = {
	id: Id;
	label: string;
	keys: string[];
};

type OptionalFilterDefinition<Id extends string> =
	| (BaseDefinition<Id> & {
			type: 'text' | 'multiline';
			placeholder?: string;
			rows?: number;
			validate?: FieldValidator<string | undefined>;
	  })
	| (BaseDefinition<Id> & {type: 'checkbox'})
	| (BaseDefinition<Id> & {type: 'advancedString'; selectableOperators: AdvancedStringFilterOperator[]})
	| (BaseDefinition<Id> & {type: 'dateRange'; popoverTitle: string; fromKey: string; toKey: string})
	| (BaseDefinition<Id> & {type: 'custom'; render: () => React.ReactNode; onRemove?: () => void});

type Props<Id extends string> = {
	definitions: OptionalFilterDefinition<Id>[];
	activeFilters: Id[];
	visibleFilters: Id[];
	onVisibleFilterChange: React.Dispatch<React.SetStateAction<Id[]>>;
	getRemoveFilterLabel: (label: string) => string;
};

const OptionalFiltersFormGroup = <Id extends string>({
	definitions,
	activeFilters,
	visibleFilters,
	onVisibleFilterChange,
	getRemoveFilterLabel,
}: Props<Id>) => {
	const form = useForm();
	const [openDateRangeFilter, setOpenDateRangeFilter] = useState<Id | undefined>();

	useEffect(() => {
		onVisibleFilterChange((currentVisibleFilters) => {
			const nextVisibleFilters = Array.from(new Set([...currentVisibleFilters, ...activeFilters]));
			return nextVisibleFilters.length === currentVisibleFilters.length ? currentVisibleFilters : nextVisibleFilters;
		});
	}, [activeFilters, onVisibleFilterChange]);

	const visibleDefinitions = visibleFilters
		.map((id) => definitions.find((definition) => definition.id === id))
		.filter((definition) => definition !== undefined);

	const renderFilter = (definition: OptionalFilterDefinition<Id>) => {
		switch (definition.type) {
			case 'advancedString':
				return (
					<AdvancedStringFilter
						name={definition.id}
						label={definition.label}
						selectableOperators={definition.selectableOperators}
					/>
				);
			case 'dateRange':
				return (
					<DateRangeField
						isModalOpen={openDateRangeFilter === definition.id}
						onModalClose={() => setOpenDateRangeFilter(undefined)}
						onClick={() => setOpenDateRangeFilter(definition.id)}
						filterName={definition.id}
						popoverTitle={definition.popoverTitle}
						label={definition.label}
						fromDateTimeKey={definition.fromKey}
						toDateTimeKey={definition.toKey}
					/>
				);
			case 'checkbox':
				return (
					<Field<boolean | undefined> name={definition.id}>
						{({input}) => (
							<Label htmlFor={definition.id} className="cursor-pointer font-normal">
								<Checkbox
									id={definition.id}
									checked={input.value === true}
									onCheckedChange={(checked) => input.onChange(checked === true)}
								/>
								{definition.label}
							</Label>
						)}
					</Field>
				);
			case 'custom':
				return definition.render();
			case 'text':
			case 'multiline':
				return (
					<Field name={definition.id} validate={definition.validate}>
						{({input}) => (
							<div className="flex flex-col gap-1.5">
								<Label htmlFor={definition.id}>{definition.label}</Label>
								{definition.type === 'multiline' ? (
									<TextAreaField
										{...input}
										id={definition.id}
										placeholder={definition.placeholder}
										rows={definition.rows}
										autoFocus
									/>
								) : (
									<TextInputField {...input} id={definition.id} placeholder={definition.placeholder} autoFocus />
								)}
							</div>
						)}
					</Field>
				);
		}
	};

	return (
		<div className="flex flex-col gap-8">
			<OptionalFiltersMenu<Id>
				visibleFilters={visibleFilters}
				optionalFilters={definitions.map(({id, label}) => ({id, label}))}
				onFilterSelect={(filter) => {
					onVisibleFilterChange((currentVisibleFilters) => Array.from(new Set([...currentVisibleFilters, filter])));
					if (definitions.find(({id}) => id === filter)?.type === 'dateRange') {
						setOpenDateRangeFilter(filter);
					}
				}}
			/>
			<div className="flex flex-col gap-5">
				{visibleDefinitions.map((definition) => (
					<div key={definition.id} className="flex items-start gap-1">
						<div className="min-w-0 flex-1">{renderFilter(definition)}</div>
						<IconButton
							className="mt-[25px]"
							variant="ghost"
							size="sm"
							icon={X}
							label={getRemoveFilterLabel(definition.label)}
							tooltipSide="top"
							onClick={() => {
								onVisibleFilterChange((currentVisibleFilters) =>
									currentVisibleFilters.filter((visibleFilter) => visibleFilter !== definition.id),
								);
								definition.keys.forEach((key) => {
									form.change(key, undefined);
								});
								if (definition.type === 'custom') {
									definition.onRemove?.();
								}
								form.submit();
							}}
						/>
					</div>
				))}
			</div>
		</div>
	);
};

export {OptionalFiltersFormGroup};
export type {OptionalFilterDefinition};
