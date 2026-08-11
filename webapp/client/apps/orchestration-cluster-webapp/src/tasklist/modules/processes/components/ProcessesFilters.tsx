/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Column, Dropdown, Grid, Search} from '@carbon/react';
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

const Fields: React.FC<FieldsProps> = ({handleSubmit, tenants}) => {
	const {t} = useTranslation();
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled && tenants.length > 1;
	const debouncedHandleSubmit = useDebounce(handleSubmit, SUBMIT_DEBOUNCE);

	return (
		<Grid narrow>
			<Column className={styles.filter} sm={4} md={isMultiTenancyEnabled ? 8 : 5} lg={10}>
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
			</Column>
			<Column
				className={styles.filter}
				sm={isMultiTenancyEnabled ? 2 : 4}
				md={isMultiTenancyEnabled ? 4 : 3}
				lg={isMultiTenancyEnabled ? 3 : 5}
			>
				<Field<FilterValues['hasStartForm']> name="hasStartForm">
					{({input}) => (
						<Dropdown
							id="process-filters"
							className={styles.dropdown}
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
			</Column>
			{isMultiTenancyEnabled ? (
				<Column className={styles.filter} sm={2} md={4} lg={2}>
					<Field<FilterValues['tenantId']> name="tenantId" initialValue={tenants[0]?.tenantId}>
						{({input}) => (
							<Dropdown
								id="tenant-filter"
								className={styles.dropdown}
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
				</Column>
			) : null}
		</Grid>
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
