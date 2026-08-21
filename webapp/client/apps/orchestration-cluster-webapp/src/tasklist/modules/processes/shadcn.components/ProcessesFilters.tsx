/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	SearchInput,
} from '@camunda/design-system';
import {useNavigate} from '@tanstack/react-router';
import {useEffect, useEffectEvent, useReducer} from 'react';
import {Field, Form, type FormRenderProps} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {getClientConfig} from '#/shared/config/getClientConfig';

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
		// gap-3 (0.75rem) and a compact, capped search width match the DS
		// ProjectDetailPage reference: filters sit together on the left as a
		// packed row, not stretched across a wide grid the way Carbon's Search did.
		<div className="flex flex-wrap items-center gap-3">
			<Label htmlFor="process-search" className="sr-only">
				{t('tasklist.processesFilterFieldLabel')}
			</Label>
			<Field<string> name="search">
				{({input}) => (
					<SearchInput
						id="process-search"
						// `className` sizes the whole search affordance per the component's own
						// contract (search-input.js) — a compact, capped width matching the DS
						// ProjectDetailPage reference, not a plain wrapper div around it.
						className="min-w-48 max-w-sm flex-1"
						placeholder={t('tasklist.processesFilterFieldLabel')}
						clearLabel={t('tasklist.processesClearFilterFieldButtonLabel')}
						value={input.value}
						onChange={(event) => {
							input.onChange(event);
							debouncedHandleSubmit();
						}}
						onClear={() => {
							input.onChange('');
							handleSubmit();
						}}
					/>
				)}
			</Field>
			<div className="min-w-0">
				<Label htmlFor="process-filters" className="sr-only">
					{t('tasklist.processesFilterDropdownLabel')}
				</Label>
				<Field<FilterValues['hasStartForm']> name="hasStartForm">
					{({input}) => {
						const selected =
							PROCESS_FILTERS.find(({hasStartForm}) => hasStartForm === input.value) ?? DEFAULT_PROCESS_FILTER;

						return (
							<Select
								value={selected.id}
								onValueChange={(id) => {
									const nextSelected = PROCESS_FILTERS.find((filter) => filter.id === id) ?? DEFAULT_PROCESS_FILTER;
									input.onChange(nextSelected.hasStartForm);
									handleSubmit();
								}}
							>
								<SelectTrigger id="process-filters" aria-label={t('tasklist.processesFilterDropdownLabel')}>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{PROCESS_FILTERS.map((filter) => (
										<SelectItem key={filter.id} value={filter.id}>
											{t(filter.translationKey)}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						);
					}}
				</Field>
			</div>
			{isMultiTenancyEnabled ? (
				<div className="min-w-0">
					<Label htmlFor="tenant-filter" className="sr-only">
						{t('tasklist.multiTenancyDropdownLabel')}
					</Label>
					<Field<FilterValues['tenantId']> name="tenantId" initialValue={tenants[0]?.tenantId}>
						{({input}) => {
							const selectedTenantId = (tenants.find(({tenantId}) => tenantId === input.value) ?? tenants[0])?.tenantId;

							return (
								<Select
									value={selectedTenantId}
									onValueChange={(tenantId) => {
										input.onChange(tenantId);
										handleSubmit();
									}}
								>
									<SelectTrigger id="tenant-filter" aria-label={t('tasklist.multiTenancyDropdownLabel')}>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{tenants.map((tenant) => (
											<SelectItem key={tenant.tenantId} value={tenant.tenantId}>
												{tenant.name} - {tenant.tenantId}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							);
						}}
					</Field>
				</div>
			) : null}
		</div>
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
