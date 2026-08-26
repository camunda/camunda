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
		<div className="flex flex-wrap items-center gap-3">
			<Label htmlFor="process-search" className="sr-only">
				{t('tasklist.processesFilterFieldLabel')}
			</Label>
			<Field<string> name="search">
				{({input}) => (
					<SearchInput
						id="process-search"
						className="min-w-48 max-w-sm flex-1"
						placeholder={t('tasklist.processesFilterFieldLabel')}
						clearLabel={t('tasklist.processesClearFilterFieldButtonLabel')}
						value={input.value}
						onChange={(event) => {
							input.onChange(event);
							debouncedHandleSubmit();
						}}
					/>
				)}
			</Field>
			<div className="min-w-0">
				<Field<FilterValues['hasStartForm']> name="hasStartForm" format={(value) => value}>
					{({input}) => {
						return (
							<>
								<Label htmlFor={input.name} className="sr-only">
									{t('tasklist.processesFilterDropdownLabel')}
								</Label>
								<Select
									defaultValue="all"
									value={input.value ?? 'all'}
									onValueChange={(value) => {
										if (!value) {
											return;
										}

										input.onChange(value === 'all' ? undefined : value);
										handleSubmit();
									}}
								>
									<SelectTrigger id={input.name} aria-label={t('tasklist.processesFilterDropdownLabel')}>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="all">{t('tasklist.processFiltersAllProcesses')}</SelectItem>
										<SelectItem value="yes">{t('tasklist.processesFormFilterRequiresForm')}</SelectItem>
										<SelectItem value="no">{t('tasklist.processesFormFilterRequiresNoForm')}</SelectItem>
									</SelectContent>
								</Select>
							</>
						);
					}}
				</Field>
			</div>
			{isMultiTenancyEnabled ? (
				<div className="min-w-0">
					<Field<FilterValues['tenantId']> name="tenantId" initialValue={tenants[0]?.tenantId}>
						{({input}) => {
							return (
								<>
									<Label htmlFor={input.name} className="sr-only">
										{t('tasklist.multiTenancyDropdownLabel')}
									</Label>
									<Select
										defaultValue={tenants[0]?.tenantId}
										value={input.value}
										onValueChange={(tenantId) => {
											if (!tenantId) {
												return;
											}

											input.onChange(tenantId);
											handleSubmit();
										}}
									>
										<SelectTrigger id={input.name} aria-label={t('tasklist.multiTenancyDropdownLabel')}>
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
								</>
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
