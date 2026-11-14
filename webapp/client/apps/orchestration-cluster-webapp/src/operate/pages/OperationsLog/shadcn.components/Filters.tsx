/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {isEqual} from 'lodash';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {useQuery} from '@tanstack/react-query';
import {Field, Form, useField, useForm} from 'react-final-form';
import {isValid, parseISO} from 'date-fns';
import {
	Combobox,
	DateTimeRangePicker,
	Heading,
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	type ComboboxOption,
} from '@camunda/design-system';
import type {
	AuditLogOperationType,
	AuditLogEntityType,
	AuditLogResult,
} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {auditLogResultSchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {FilterSidebar} from '#/operate/shared/FilterSidebar/shadcn.components/FilterSidebar';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {TenantField} from '#/operate/shared/TenantField/shadcn.components/TenantField';
import {TextInputField} from '#/operate/shared/TextInputField/shadcn.components/TextInputField';
import {formatISODate} from '#/operate/shared/DateRangeField/formatDate';
import {getTimeZoneLabel} from '#/operate/shared/DateRangeField/getTimeZoneLabel';
import {getCurrentDateLocale} from '#/shared/i18n';
import {FilterMultiSelect} from '#/operate/shared/FilterMultiSelect/shadcn.components/FilterMultiSelect';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES, AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES} from '../operationsLogFilters';
import {
	operationsLogDefinitionsQuery,
	selectedDefinitionsQuery,
} from '#/operate/shared/queries/processDefinitions.queries';
import type {OperationsLogSearch} from '../operationsLog.schema';
import {DefinitionAlert} from './DefinitionAlert';

const ALL_VERSIONS = 'all';
const ALL_RESULTS = 'all';

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

type ProcessItem = {
	id: string;
	tenantId: string;
	version: number | undefined;
	label: string;
};

const EMPTY_FILTERS: FormValues = {};

const getProcessItemKey = ({tenantId, id}: Pick<ProcessItem, 'tenantId' | 'id'>) => JSON.stringify([tenantId, id]);

const toDate = (value: string | undefined) => {
	if (value === undefined || value === '') {
		return undefined;
	}
	const date = parseISO(value);
	return isValid(date) ? date : undefined;
};

const TimestampRangeField: React.FC = () => {
	const {t} = useTranslation();
	const form = useForm();
	const after = useField<string | undefined>('timestampAfter').input.value;
	const before = useField<string | undefined>('timestampBefore').input.value;
	const value = useMemo(() => ({from: toDate(after), to: toDate(before)}), [after, before]);

	return (
		<DateTimeRangePicker
			id="timestamp-range"
			label={t('operate.operationsLog.filters.timestampDateRange')}
			value={value}
			locale={getCurrentDateLocale()}
			messages={{
				placeholder: t('operate.shared.dateRangeField.enterDateRange'),
				fromTime: t('operate.shared.dateRangeField.fromTime'),
				toTime: t('operate.shared.dateRangeField.toTime'),
				timeZone: t('operate.shared.dateTimeRangeField.timeZone', {timeZone: getTimeZoneLabel()}),
				endBeforeStart: t('operate.shared.dateTimeRangeField.endBeforeStart'),
				apply: t('operate.shared.dateRangeField.apply'),
				cancel: t('operate.shared.dateRangeField.cancel'),
				clearRange: t('operate.shared.dateTimeRangePicker.clearRange'),
				selectEndDate: t('operate.shared.dateTimeRangePicker.selectEndDate'),
				pickDayFirst: t('operate.shared.dateTimeRangePicker.pickDayFirst'),
				pickEndDay: t('operate.shared.dateTimeRangePicker.pickEndDay'),
			}}
			onChange={(range) => {
				form.change('timestampAfter', range.from ? formatISODate(range.from) : undefined);
				form.change('timestampBefore', range.to ? formatISODate(range.to) : undefined);
			}}
		/>
	);
};

const Filters: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;

	const {sort: _sort, ...filterValues} = search;

	const {
		data: processDefinitions,
		isPending: isProcessListPending,
		isError: isProcessListError,
	} = useQuery({
		...operationsLogDefinitionsQuery({
			isLatestVersion: true,
			...(isSpecificTenant(search.tenantId) ? {tenantId: search.tenantId} : {}),
		}),
		enabled: !isMultiTenancyEnabled || search.tenantId !== undefined,
		retry: false,
	});
	const {data: currentUser} = useQuery({...queries.getCurrentUser(), enabled: isMultiTenancyEnabled});
	const tenantNames = useMemo(
		() => Object.fromEntries(currentUser?.tenants.map(({tenantId, name}) => [tenantId, name]) ?? []),
		[currentUser],
	);
	const processItems = useMemo<ProcessItem[]>(() => {
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
		<Form<FormValues> onSubmit={handleFiltersSubmit} initialValues={filterValues}>
			{({handleSubmit, form, values}) => (
				<form onSubmit={handleSubmit} className="flex h-full min-h-0">
					<AutoSubmit
						fieldsToSkipTimeout={['tenantId', 'process', 'version', 'operationType', 'entityType', 'result']}
					/>
					<FilterSidebar
						localStorageKey="isAuditLogsFiltersCollapsed"
						isResetButtonDisabled={isEqual(EMPTY_FILTERS, values)}
						onResetClick={() => {
							form.reset();
							void navigate({to: '.', search: {}});
						}}
					>
						<div className="flex flex-col gap-8">
							{isMultiTenancyEnabled && (
								<section className="flex flex-col gap-3">
									<Heading as="h3" variant="heading-xs">
										{t('operate.operationsLog.filters.tenant')}
									</Heading>
									<TenantField
										onChange={() => {
											form.change('process', undefined);
											form.change('version', undefined);
											form.change('allVersions', undefined);
										}}
									/>
								</section>
							)}
							<section className="flex flex-col gap-3">
								<Heading as="h3" variant="heading-xs">
									{t('operate.operationsLog.filters.processSection')}
								</Heading>
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
										const unlistedProcess: ProcessItem | undefined =
											input.value && !selectedProcess
												? {id: input.value, tenantId: specificTenantId ?? '', version: undefined, label: input.value}
												: undefined;
										const allItems = unlistedProcess ? [...items, unlistedProcess] : items;
										const options: ComboboxOption[] = allItems.map((item) => ({
											value: getProcessItemKey(item),
											label: item.label,
										}));
										const selectedItem = selectedProcess ?? unlistedProcess;

										return (
											<div className="flex flex-col gap-1.5">
												<Label htmlFor="process-filter">{t('operate.processes.filters.name')}</Label>
												<Combobox
													id="process-filter"
													className="w-full"
													size="sm"
													options={options}
													value={selectedItem ? getProcessItemKey(selectedItem) : ''}
													placeholder={t('operate.processes.ds.filters.searchByName')}
													searchPlaceholder={t('operate.processes.ds.filters.searchByName')}
													emptyIndicator={t('operate.operationsLog.filters.noProcessesFound')}
													disabled={
														isProcessListPending || isProcessListError || (isMultiTenancyEnabled && !values.tenantId)
													}
													onValueChange={(key) => {
														const selected = allItems.find((item) => getProcessItemKey(item) === key);
														if (
															selected &&
															selected.id === input.value &&
															(values.tenantId === undefined || selected.tenantId === values.tenantId)
														) {
															return;
														}
														input.onChange(selected?.id);
														form.change('version', selected?.version);
														form.change('allVersions', undefined);
														if (isMultiTenancyEnabled && selected) {
															form.change('tenantId', selected.tenantId);
														}
													}}
												/>
											</div>
										);
									}}
								</Field>
								<Field name="version">
									{({input}) => (
										<ProcessVersionSelect
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
									<DefinitionAlert
										variant="destructive"
										title={t('operate.operationsLog.filters.definitionsListFailed')}
									/>
								)}
								<Field name="processInstanceKey">
									{({input}) => (
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="process-instance-key">
												{t('operate.operationsLog.filters.processInstanceKey')}
											</Label>
											<TextInputField
												{...input}
												id="process-instance-key"
												size="sm"
												type="text"
												placeholder={t('operate.operationsLog.filters.processInstanceKeyPlaceholder')}
											/>
										</div>
									)}
								</Field>
							</section>
							<section className="flex flex-col gap-3">
								<Heading as="h3" variant="heading-xs">
									{t('operate.operationsLog.filters.operationSection')}
								</Heading>
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
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="result-field">{t('operate.operationsLog.filters.operationsStatus')}</Label>
											<Select
												value={input.value}
												onValueChange={(value) => input.onChange(value === ALL_RESULTS ? undefined : value)}
											>
												<SelectTrigger id="result-field" size="sm" className="w-full">
													<SelectValue placeholder={t('operate.operationsLog.filters.chooseOption')} />
												</SelectTrigger>
												<SelectContent>
													<SelectItem value={ALL_RESULTS}>{t('operate.operationsLog.filters.all')}</SelectItem>
													{auditLogResultSchema.options.map((option) => (
														<SelectItem key={option} value={option}>
															{spaceAndCapitalize(option)}
														</SelectItem>
													))}
												</SelectContent>
											</Select>
										</div>
									)}
								</Field>
								<Field name="actorId">
									{({input}) => (
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="actorId">{t('operate.operationsLog.filters.actor')}</Label>
											<TextInputField
												{...input}
												id="actorId"
												size="sm"
												type="text"
												placeholder={t('operate.operationsLog.filters.actorPlaceholder')}
											/>
										</div>
									)}
								</Field>
								<TimestampRangeField />
							</section>
						</div>
					</FilterSidebar>
				</form>
			)}
		</Form>
	);
};

const ProcessVersionSelect: React.FC<{
	process?: string;
	tenantId?: string;
	value?: number;
	isAllVersionsSelected: boolean;
	onChange: (value?: number) => void;
}> = ({process, tenantId, value, isAllVersionsSelected, onChange}) => {
	const {t} = useTranslation();
	const {
		data: definitions,
		isPending,
		isError,
	} = useQuery({
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
	const versions: {version: number | undefined; state: string | undefined}[] =
		distinctVersions.length > 1 || isAllVersionsSelected
			? [{version: undefined, state: undefined}, ...distinctVersions]
			: distinctVersions;
	const items =
		value !== undefined && !versions.some((item) => item.version === value)
			? [...versions, {version: value, state: undefined}]
			: versions;
	const selectedValue = value === undefined ? (isAllVersionsSelected ? ALL_VERSIONS : '') : String(value);

	return (
		<>
			<div className="flex flex-col gap-1.5">
				<Label htmlFor="process-version-filter">{t('operate.processes.filters.version')}</Label>
				<Select
					value={selectedValue}
					disabled={!process || isPending || !matchingDefinitions?.length || !isUnambiguous}
					onValueChange={(next) => {
						if (next === '') {
							return;
						}
						onChange(next === ALL_VERSIONS ? undefined : Number(next));
					}}
				>
					<SelectTrigger id="process-version-filter" size="sm" className="w-full">
						<SelectValue placeholder={t('operate.processes.ds.filters.selectVersion')} />
					</SelectTrigger>
					<SelectContent>
						{items.map((item) => (
							<SelectItem
								key={item.version ?? ALL_VERSIONS}
								value={item.version === undefined ? ALL_VERSIONS : String(item.version)}
							>
								{item.version === undefined
									? t('operate.processes.filters.allVersions')
									: item.state === 'DELETED'
										? t('operate.operationsLog.filters.deletedVersion', {version: item.version})
										: String(item.version)}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</div>
			{isError && (
				<DefinitionAlert variant="destructive" title={t('operate.operationsLog.filters.definitionsListFailed')} />
			)}
		</>
	);
};

export {Filters};
