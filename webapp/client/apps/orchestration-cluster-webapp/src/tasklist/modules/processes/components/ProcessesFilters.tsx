/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Column, Dropdown, Grid, Search} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import {useNavigate} from '@tanstack/react-router';
import {useEffect, useEffectEvent, useReducer} from 'react';
import {Field, Form, type FormRenderProps} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {getClientConfig} from '#/shared/config/getClientConfig';
import styles from './ProcessesFilters.module.scss';

const SUBMIT_DEBOUNCE = 500;

const DEFAULT_PROCESS_FILTER = {
	id: 'all',
	translationKey: 'tasklist.processFiltersAllProcesses',
	hasStartForm: undefined,
} as const;

const PROCESS_FILTERS = [
	DEFAULT_PROCESS_FILTER,
	{
		id: 'requires-form',
		translationKey: 'tasklist.processesFormFilterRequiresForm',
		hasStartForm: 'yes',
	} as const,
	{
		id: 'requires-no-form',
		translationKey: 'tasklist.processesFormFilterRequiresNoForm',
		hasStartForm: 'no',
	} as const,
];

type FilterValues = {
	search: string;
	hasStartForm?: 'yes' | 'no';
	tenantId?: string;
};

type Props = {
	initialFilterValues: Partial<FilterValues>;
	tenants: CurrentUser['tenants'];
};

type FieldsProps = {
	handleSubmit: FormRenderProps<FilterValues>['handleSubmit'];
	tenants: CurrentUser['tenants'];
};

function useDebounce(callback: () => void, delay: number) {
	const onDebounce = useEffectEvent(callback);
	const [invocationCount, invoke] = useReducer((count: number) => count + 1, 0);

	useEffect(() => {
		if (invocationCount === 0) {
			return;
		}

		const timeoutId = setTimeout(() => onDebounce(), delay);

		return () => clearTimeout(timeoutId);
	}, [delay, invocationCount]);

	return invoke;
}

// Carbon's Grid/Column have no real DS adapter — carbon-compat passes them
// straight through with "MIGRATION TODO: replace with Tailwind grid utilities".
// On the DS path these render a plain CSS grid (see the module SCSS) carrying the
// same sm/md/lg spans; on the Carbon path they stay Carbon's own Grid/Column so
// the flag-off layout is untouched.
type GridCellProps = {
	sm: number;
	md: number;
	lg: number;
	className?: string;
	children: React.ReactNode;
};

const FilterGrid: React.FC<{children: React.ReactNode}> = ({children}) =>
	featureFlags.dsTasklistUI ? <div className={styles.gridDS}>{children}</div> : <Grid narrow>{children}</Grid>;

const GridCell: React.FC<GridCellProps> = ({sm, md, lg, className, children}) => {
	if (featureFlags.dsTasklistUI) {
		// The sm/md/lg spans are Carbon-grid concepts and are deliberately unused
		// here — the DS path lays the filters out as a flex row instead.
		return <div className={cn(styles.cellDS, className)}>{children}</div>;
	}

	return (
		<Column className={className} sm={sm} md={md} lg={lg}>
			{children}
		</Column>
	);
};

const Fields: React.FC<FieldsProps> = ({handleSubmit, tenants}) => {
	const {t} = useTranslation();
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled && tenants.length > 1;
	const debouncedHandleSubmit = useDebounce(handleSubmit, SUBMIT_DEBOUNCE);

	return (
		<FilterGrid>
			<GridCell
				className={cn(
					styles.filter,
					featureFlags.dsTasklistUI && styles.filterDS,
					featureFlags.dsTasklistUI && styles.searchCellDS,
				)}
				sm={4}
				md={isMultiTenancyEnabled ? 8 : 5}
				lg={10}
			>
				<Field<string> name="search">
					{({input}) => (
						<Search
							id="process-search"
							size="md"
							placeholder={t('tasklist.processesFilterFieldLabel')}
							labelText={t('tasklist.processesFilterFieldLabel')}
							closeButtonLabelText={t('tasklist.processesClearFilterFieldButtonLabel')}
							value={input.value}
							onChange={(event) => {
								input.onChange(event);
								debouncedHandleSubmit();
							}}
						/>
					)}
				</Field>
			</GridCell>
			<GridCell
				className={cn(styles.filter, featureFlags.dsTasklistUI && styles.filterDS)}
				sm={isMultiTenancyEnabled ? 2 : 4}
				md={isMultiTenancyEnabled ? 4 : 3}
				lg={isMultiTenancyEnabled ? 3 : 5}
			>
				<Field<FilterValues['hasStartForm']> name="hasStartForm">
					{({input}) => (
						<Dropdown
							id="process-filters"
							className={cn(styles.dropdown, featureFlags.dsTasklistUI && styles.dropdownLabelHiddenDS)}
							hideLabel
							selectedItem={
								PROCESS_FILTERS.find(({hasStartForm}) => hasStartForm === input.value) ?? DEFAULT_PROCESS_FILTER
							}
							titleText={t('tasklist.processesFilterDropdownLabel')}
							label={t('tasklist.processesFilterDropdownLabel')}
							items={PROCESS_FILTERS}
							itemToString={(item) => (item ? t(item.translationKey) : '')}
							onChange={({selectedItem}) => {
								input.onChange(selectedItem?.hasStartForm);
								handleSubmit();
							}}
						/>
					)}
				</Field>
			</GridCell>
			{isMultiTenancyEnabled ? (
				<GridCell className={cn(styles.filter, featureFlags.dsTasklistUI && styles.filterDS)} sm={2} md={4} lg={2}>
					<Field<FilterValues['tenantId']> name="tenantId" initialValue={tenants[0]?.tenantId}>
						{({input}) => (
							<Dropdown
								id="tenant-filter"
								className={cn(styles.dropdown, featureFlags.dsTasklistUI && styles.dropdownLabelHiddenDS)}
								hideLabel
								selectedItem={tenants.find(({tenantId}) => tenantId === input.value) ?? tenants[0]}
								titleText={t('tasklist.multiTenancyDropdownLabel')}
								label={t('tasklist.multiTenancyDropdownLabel')}
								items={tenants}
								itemToString={(item) => (item ? `${item.name} - ${item.tenantId}` : '')}
								onChange={({selectedItem}) => {
									input.onChange(selectedItem?.tenantId);
									handleSubmit();
								}}
							/>
						)}
					</Field>
				</GridCell>
			) : null}
		</FilterGrid>
	);
};

const ProcessesFilters: React.FC<Props> = ({initialFilterValues, tenants}) => {
	const navigate = useNavigate();

	return (
		<Form<FilterValues>
			initialValues={{...initialFilterValues, search: initialFilterValues.search ?? ''}}
			onSubmit={({search, hasStartForm, tenantId}) =>
				navigate({
					to: '.',
					search: (previousSearch) => ({
						...previousSearch,
						search: search || undefined,
						hasStartForm,
						tenantId,
					}),
				})
			}
		>
			{({handleSubmit}) => (
				<form onSubmit={handleSubmit}>
					<Fields handleSubmit={handleSubmit} tenants={tenants} />
				</form>
			)}
		</Form>
	);
};

export {ProcessesFilters};
