/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {isEqual} from 'lodash';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {useQuery} from '@tanstack/react-query';
import {Field, Form} from 'react-final-form';
import {ComboBox, Dropdown, InlineNotification, Stack} from '@carbon/react';
import type {
	AuditLogOperationType,
	AuditLogEntityType,
	AuditLogResult,
} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {auditLogResultSchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/FiltersPanel';
import {Title, Form as StyledForm} from '#/operate/shared/FiltersPanel/styled';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {TenantField} from '#/operate/shared/TenantField/TenantField';
import {TextInputField} from '#/operate/shared/TextInputField/TextInputField';
import {DateRangeField} from '#/operate/shared/DateRangeField/DateRangeField';
import {FilterMultiSelect} from '#/operate/shared/FilterMultiSelect/FilterMultiSelect';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES, AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES} from './operationsLogFilters';
import {operationsLogDefinitionsQuery, selectedDefinitionsQuery} from './definitions.queries';
import type {OperationsLogSearch} from './operationsLog.schema';

type Props = {
	search: OperationsLogSearch;
};

type FormValues = {
	tenantId?: string;
	process?: string;
	version?: number;
	allVersions?: boolean;
	processInstanceKey?: string;
	operationType?: AuditLogOperationType[];
	entityType?: AuditLogEntityType[];
	result?: AuditLogResult;
	actorId?: string;
	timestampAfter?: string;
	timestampBefore?: string;
};

const EMPTY_FILTERS: FormValues = {};

const Filters: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [isDateRangeModalOpen, setIsDateRangeModalOpen] = useState(false);
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;

	const {sort: _sort, ...filterValues} = search;

	const {
		data: processDefinitions,
		isPending: isProcessListPending,
		isError: isProcessListError,
	} = useQuery({
		...operationsLogDefinitionsQuery({isLatestVersion: true}),
		retry: false,
	});
	const {data: currentUser} = useQuery({...queries.getCurrentUser(), enabled: isMultiTenancyEnabled});
	const tenantNames = useMemo(
		() => Object.fromEntries(currentUser?.tenants.map(({tenantId, name}) => [tenantId, name]) ?? []),
		[currentUser],
	);
	const processItems = useMemo(() => {
		const definitions = new Map(
			(processDefinitions ?? []).map((definition) => [
				JSON.stringify([definition.tenantId, definition.processDefinitionId]),
				definition,
			]),
		);
		return [...definitions.values()]
			.map((definition) => ({
				id: definition.processDefinitionId,
				tenantId: definition.tenantId,
				version: definition.version,
				label:
					isMultiTenancyEnabled && !isSpecificTenant(search.tenantId)
						? `${definition.name ?? definition.processDefinitionId} - ${tenantNames[definition.tenantId] ?? definition.tenantId}`
						: (definition.name ?? definition.processDefinitionId),
			}))
			.sort((a, b) => a.label.localeCompare(b.label));
	}, [processDefinitions, isMultiTenancyEnabled, search.tenantId, tenantNames]);

	const handleFiltersSubmit = (values: FormValues) => {
		void navigate({
			to: '.',
			search: (prev) => ({
				...prev,
				...((values.tenantId || undefined) !== prev.tenantId ? {process: undefined, version: undefined} : {}),
				tenantId: values.tenantId || undefined,
				process: values.process || undefined,
				version: values.version,
				allVersions: values.allVersions || undefined,
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
		<Form<FormValues>
			key={`${search.tenantId ?? ''}:${search.process ?? ''}:${search.version ?? ''}:${search.allVersions ?? ''}`}
			onSubmit={handleFiltersSubmit}
			initialValues={filterValues}
		>
			{({handleSubmit, form, values}) => (
				<StyledForm onSubmit={handleSubmit}>
					<AutoSubmit
						fieldsToSkipTimeout={['tenantId', 'process', 'version', 'operationType', 'entityType', 'result']}
					/>
					<FiltersPanel
						localStorageKey="isAuditLogsFiltersCollapsed"
						isResetButtonDisabled={isEqual(EMPTY_FILTERS, values)}
						onResetClick={() => {
							form.reset();
							void navigate({to: '.', search: {}});
						}}
					>
						<Stack gap={5}>
							{isMultiTenancyEnabled && (
								<div>
									<Title>{t('operate.operationsLog.filters.tenant')}</Title>
									<TenantField
										onChange={() => {
											form.change('process', undefined);
											form.change('version', undefined);
											form.change('allVersions', undefined);
										}}
									/>
								</div>
							)}
							<div>
								<Title>{t('operate.operationsLog.filters.processSection')}</Title>
								<Stack gap={5}>
									<Field name="process">
										{({input}) => {
											const specificTenantId = isSpecificTenant(values.tenantId) ? values.tenantId : undefined;
											const items = specificTenantId
												? processItems.filter((item) => item.tenantId === specificTenantId)
												: processItems;
											const matches = items.filter(
												(item) => item.id === input.value && (!specificTenantId || item.tenantId === specificTenantId),
											);
											const selectedProcess = matches.length === 1 ? matches[0] : undefined;
											return (
												<ComboBox
													id="process-filter"
													titleText={t('operate.processes.filters.name')}
													placeholder={t('operate.processes.filters.searchByName')}
													items={items}
													itemToString={(item) => item?.label ?? ''}
													selectedItem={
														input.value
															? (selectedProcess ?? {
																	id: input.value,
																	tenantId: specificTenantId ?? '',
																	version: undefined,
																	label: input.value,
																})
															: null
													}
													disabled={
														isProcessListPending || isProcessListError || (isMultiTenancyEnabled && !values.tenantId)
													}
													size="sm"
													onChange={({selectedItem}) => {
														if (
															selectedItem &&
															selectedItem.id === input.value &&
															(values.tenantId === undefined || selectedItem.tenantId === values.tenantId)
														) {
															return;
														}
														input.onChange(selectedItem?.id);
														form.change('version', selectedItem?.version);
														form.change('allVersions', undefined);
														if (isMultiTenancyEnabled && selectedItem) {
															form.change('tenantId', selectedItem.tenantId);
														}
													}}
												/>
											);
										}}
									</Field>
									<Field name="version">
										{({input}) => (
											<ProcessVersionDropdown
												process={values.process}
												tenantId={values.tenantId}
												value={input.value === '' ? undefined : input.value}
												isAllVersionsSelected={values.allVersions === true}
												onChange={(version) => {
													input.onChange(version);
													form.change('allVersions', version === undefined ? true : undefined);
												}}
											/>
										)}
									</Field>
									{isProcessListError && (
										<InlineNotification kind="error" title={t('operate.operationsLog.filters.definitionsListFailed')} />
									)}
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

const ProcessVersionDropdown: React.FC<{
	process?: string;
	tenantId?: string;
	value?: number;
	isAllVersionsSelected: boolean;
	onChange: (value?: number) => void;
}> = ({process, tenantId, value, isAllVersionsSelected, onChange}) => {
	const {t} = useTranslation();
	const {data: definitions, isPending} = useQuery({
		...selectedDefinitionsQuery(process ?? '', tenantId),
		enabled: Boolean(process),
		retry: false,
	});
	const matchingDefinitions = definitions?.filter(
		(definition) =>
			definition.processDefinitionId === process && (!isSpecificTenant(tenantId) || definition.tenantId === tenantId),
	);
	const isUnambiguous =
		isSpecificTenant(tenantId) || new Set(matchingDefinitions?.map((definition) => definition.tenantId)).size === 1;
	const distinctVersions = [
		...new Map(matchingDefinitions?.map((definition) => [definition.version, definition]) ?? []).values(),
	].sort((a, b) => b.version - a.version);
	const versions =
		distinctVersions.length > 1 || isAllVersionsSelected
			? [{version: undefined, state: undefined}, ...distinctVersions]
			: distinctVersions;
	return (
		<Dropdown
			id="process-version-filter"
			titleText={t('operate.processes.filters.version')}
			label={t('operate.processes.filters.selectVersion')}
			items={
				value !== undefined && !versions.some((item) => item.version === value)
					? [...versions, {version: value, state: undefined}]
					: versions
			}
			itemToString={(item) =>
				item?.version === undefined
					? t('operate.processes.filters.allVersions')
					: item.state === 'DELETED'
						? t('operate.operationsLog.filters.deletedVersion', {version: item.version})
						: String(item.version)
			}
			selectedItem={
				value === undefined && !isAllVersionsSelected
					? null
					: (versions.find((item) => item.version === value) ?? {version: value, state: undefined})
			}
			disabled={!process || isPending || !matchingDefinitions?.length || !isUnambiguous}
			size="sm"
			onChange={({selectedItem}) => onChange(selectedItem?.version)}
		/>
	);
};

export {Filters};
