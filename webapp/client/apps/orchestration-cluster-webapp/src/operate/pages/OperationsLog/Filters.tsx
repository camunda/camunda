/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {Field, Form} from 'react-final-form';
import {Dropdown, Stack} from '@carbon/react';
import type {
	AuditLogOperationType,
	AuditLogEntityType,
	AuditLogResult,
} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {auditLogResultSchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/FiltersPanel';
import {Title, Form as StyledForm} from '#/operate/shared/FiltersPanel/styled';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {TenantField} from '#/operate/shared/TenantField/TenantField';
import {TextInputField} from '#/operate/shared/TextInputField/TextInputField';
import {DateRangeField} from '#/operate/shared/DateRangeField/DateRangeField';
import {FilterMultiSelect} from '#/operate/shared/FilterMultiSelect/FilterMultiSelect';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES, AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES} from './operationsLogFilters';
import type {OperationsLogSearch} from './operationsLog.schema';

type Props = {
	search: OperationsLogSearch;
};

type FormValues = {
	tenantId?: string;
	process?: string;
	version?: number;
	processInstanceKey?: string;
	operationType?: AuditLogOperationType[];
	entityType?: AuditLogEntityType[];
	result?: AuditLogResult;
	actorId?: string;
	timestampAfter?: string;
	timestampBefore?: string;
};

const Filters: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [isDateRangeModalOpen, setIsDateRangeModalOpen] = useState(false);

	const {data: processDefinitions} = useSuspenseQuery(queries.queryProcessDefinitions({page: {limit: 1000}}));

	const versionNumbers = search.process
		? [
				undefined,
				...processDefinitions.items
					.filter((def) => def.processDefinitionId === search.process)
					.sort((a, b) => b.version - a.version)
					.map((def) => def.version),
			]
		: [];

	const isResetDisabled =
		search.tenantId === undefined &&
		search.process === undefined &&
		search.version === undefined &&
		search.processInstanceKey === undefined &&
		search.operationType === undefined &&
		search.entityType === undefined &&
		search.result === undefined &&
		search.actorId === undefined &&
		search.timestampAfter === undefined &&
		search.timestampBefore === undefined;

	const handleFiltersSubmit = (values: FormValues) => {
		void navigate({
			to: '.',
			search: (prev) => ({
				...prev,
				...((values.tenantId || undefined) !== prev.tenantId ? {process: undefined, version: undefined} : {}),
				tenantId: values.tenantId || undefined,
				process: values.process || undefined,
				version: values.version,
				processInstanceKey: values.processInstanceKey || undefined,
				operationType: values.operationType?.length ? values.operationType : undefined,
				entityType: values.entityType?.length ? values.entityType : undefined,
				result: values.result || undefined,
				actorId: values.actorId || undefined,
				timestampAfter: values.timestampAfter || undefined,
				timestampBefore: values.timestampBefore || undefined,
			}),
		});
	};

	return (
		<Form<FormValues> onSubmit={handleFiltersSubmit} initialValues={search}>
			{({handleSubmit, form}) => (
				<StyledForm onSubmit={handleSubmit}>
					<AutoSubmit
						fieldsToSkipTimeout={['tenantId', 'process', 'version', 'operationType', 'entityType', 'result']}
					/>
					<FiltersPanel
						localStorageKey="isAuditLogsFiltersCollapsed"
						isResetButtonDisabled={isResetDisabled}
						onResetClick={() => {
							form.reset();
							void navigate({to: '.', search: {}});
						}}
					>
						<Stack gap={5}>
							{getClientConfig().deployment.isMultiTenancyEnabled && (
								<div>
									<Title>{t('operate.operationsLog.filters.tenant')}</Title>
									<TenantField
										onChange={() => {
											form.change('process', undefined);
											form.change('version', undefined);
										}}
									/>
								</div>
							)}
							<div>
								<Title>{t('operate.operationsLog.filters.processSection')}</Title>
								<Stack gap={5}>
									<Field name="process">
										{({input}) => (
											<Dropdown
												id="process-filter"
												titleText={t('operate.processes.filters.name')}
												label={t('operate.processes.filters.searchByName')}
												items={['', ...new Set(processDefinitions.items.map((def) => def.processDefinitionId))]}
												itemToString={(item) => {
													if (!item) {
														return '';
													}
													return processDefinitions.items.find((def) => def.processDefinitionId === item)?.name ?? item;
												}}
												selectedItem={input.value ?? ''}
												size="sm"
												onChange={({selectedItem}) => {
													input.onChange(selectedItem || undefined);
													form.change('version', undefined);
												}}
											/>
										)}
									</Field>
									<Field name="version">
										{({input}) => (
											<Dropdown
												id="process-version-filter"
												titleText={t('operate.processes.filters.version')}
												label={t('operate.processes.filters.selectVersion')}
												items={versionNumbers}
												itemToString={(item) =>
													item === undefined || item === null
														? t('operate.processes.filters.allVersions')
														: String(item)
												}
												selectedItem={input.value}
												disabled={!search.process}
												size="sm"
												onChange={({selectedItem}) => input.onChange(selectedItem ?? undefined)}
											/>
										)}
									</Field>
									<Field name="processInstanceKey">
										{({input}) => (
											<TextInputField
												{...input}
												id="process-instance-key"
												size="sm"
												labelText={t('operate.operationsLog.filters.processInstanceKey')}
												type="text"
												placeholder={t('operate.operationsLog.filters.processInstanceKeyPlaceholder')}
											/>
										)}
									</Field>
								</Stack>
							</div>
							<div>
								<Title>{t('operate.operationsLog.filters.operationSection')}</Title>
								<Stack gap={5}>
									<FilterMultiSelect
										name="operationType"
										titleText={t('operate.operationsLog.filters.operationType')}
										items={AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES}
									/>
									<FilterMultiSelect
										name="entityType"
										titleText={t('operate.operationsLog.filters.entityType')}
										items={AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES}
									/>
									<Field name="result">
										{({input}) => (
											<Dropdown
												label={t('operate.operationsLog.filters.chooseOption')}
												aria-label={t('operate.operationsLog.filters.chooseOption')}
												titleText={t('operate.operationsLog.filters.operationsStatus')}
												id="result-field"
												onChange={({selectedItem}) => input.onChange(selectedItem === 'all' ? undefined : selectedItem)}
												items={['all', ...auditLogResultSchema.options]}
												itemToString={(item) =>
													item === 'all' ? t('operate.operationsLog.filters.all') : spaceAndCapitalize(item)
												}
												selectedItem={input.value}
												size="sm"
											/>
										)}
									</Field>
									<Field name="actorId">
										{({input}) => (
											<TextInputField
												{...input}
												id="actorId"
												size="sm"
												labelText={t('operate.operationsLog.filters.actor')}
												type="text"
												placeholder={t('operate.operationsLog.filters.actorPlaceholder')}
											/>
										)}
									</Field>
									<DateRangeField
										isModalOpen={isDateRangeModalOpen}
										onModalClose={() => setIsDateRangeModalOpen(false)}
										onClick={() => setIsDateRangeModalOpen(true)}
										filterName="timestamp"
										popoverTitle={t('operate.operationsLog.filters.filterByTimestampDateRange')}
										label={t('operate.operationsLog.filters.timestampDateRange')}
										fromDateTimeKey="timestampAfter"
										toDateTimeKey="timestampBefore"
									/>
								</Stack>
							</div>
						</Stack>
					</FiltersPanel>
				</StyledForm>
			)}
		</Form>
	);
};

export {Filters};
