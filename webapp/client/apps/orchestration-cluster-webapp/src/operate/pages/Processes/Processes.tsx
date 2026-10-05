/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery, useSuspenseQuery} from '@tanstack/react-query';
import {useBlocker, useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Button, Checkbox, ComboBox, Dropdown, Stack} from '@carbon/react';
import {queries} from '#/shared/http/queries';
import {ForbiddenError} from '#/shared/errors';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {ProcessesLayout, type ProcessesMode} from './ProcessesLayout';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/FiltersPanel';
import {Title, Form as StyledForm} from '#/operate/shared/FiltersPanel/styled';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {TenantField} from '#/operate/shared/TenantField/TenantField';
import {
	RadioButtonChecked,
	WarningFilled,
	CheckmarkOutline,
	PauseOutlineFilled,
} from '#/operate/shared/StateIcon/styled';
import {IndentedGroup, CanceledIcon} from './styled';
import {
	OptionalFiltersFormGroup,
	type OptionalFilter,
	type OptionalFilterValues,
	type VariableFieldValues,
} from './OptionalFiltersFormGroup';
import {DiagramPanel, type ProcessDefinitionSelection} from './DiagramPanel';
import {InstancesTable} from './InstancesTable';
import {MigrationView} from './MigrationView';
import {ENABLE_PROCESS_MIGRATION} from '#/shared/feature-flags';
import type {MigrationScope} from './getMigrationFilter';
import {useDiagramXml} from './useDiagramXml';
import {selectedDefinitionsQuery} from '#/operate/shared/queries/processDefinitions.queries';
import type {BatchModificationScope} from './useBatchModificationStatistics';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {setVariableConditions, useVariableConditions} from './VariablesFilter/variableFilterStore';

type FiltersFormValues = OptionalFilterValues & VariableFieldValues & {tenantId?: string};

type Props = {
	process?: string;
	version?: number;
	elementId?: string;
	incidentErrorHashCode?: number;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
	suspended: boolean;
	sort?: string;
} & OptionalFilterValues & {tenantId?: string};

type ProcessItem = {id: string; label: string};

const SESSION_FILTER_FIELDS = new Set(['variableName', 'variableValues']);

// The variable fields follow session storage; re-initialising on their change would discard unsubmitted URL filter edits.
function areUrlFilterValuesEqual(previous: Record<string, unknown> = {}, next: Record<string, unknown> = {}) {
	const keys = new Set([...Object.keys(previous), ...Object.keys(next)]);
	return [...keys].every((key) => SESSION_FILTER_FIELDS.has(key) || previous[key] === next[key]);
}

const Processes: React.FC<Props> = ({
	process,
	version,
	elementId,
	active,
	incidents,
	completed,
	canceled,
	suspended,
	tenantId,
	processInstanceKey,
	parentProcessInstanceKey,
	businessId,
	batchOperationKey,
	errorMessage,
	incidentErrorHashCode,
	hasRetriesLeft,
	startDateFrom,
	startDateTo,
	endDateFrom,
	endDateTo,
	sort,
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const specificTenantId = isSpecificTenant(tenantId) ? tenantId : undefined;
	const {data} = useSuspenseQuery(
		queries.queryProcessDefinitions({
			page: {limit: 1000},
			filter: specificTenantId ? {tenantId: specificTenantId} : undefined,
		}),
	);
	const {
		data: selectedDefinitions,
		error: selectedDefinitionsError,
		isPending: isDefinitionsPending,
		isFetching: isDefinitionsFetching,
		isError: isDefinitionsError,
		refetch: refetchSelectedDefinitions,
	} = useQuery({
		...selectedDefinitionsQuery(process ?? '', specificTenantId, {retry: false, requireComplete: true}),
		enabled: Boolean(process),
		retry: false,
	});
	const isDefinitionsLoading =
		Boolean(process) && (isDefinitionsPending || (isDefinitionsError && isDefinitionsFetching));
	const isDefinitionsReady =
		Boolean(process) && selectedDefinitions !== undefined && !isDefinitionsLoading && !isDefinitionsError;
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
	const [mode, setMode] = useState<ProcessesMode>('list');
	const [selectedTargetElementId, setSelectedTargetElementId] = useState<string>();
	const [selectionScope, setSelectionScope] = useState<BatchModificationScope | null>(null);
	const [migration, setMigration] = useState<{source: ProcessDefinition; scope: MigrationScope} | null>(null);
	const blocker = useBlocker({
		// Like legacy, only a change of page interrupts the mode; search-only changes, such as browser back, pass.
		shouldBlockFn: ({current, next}) => current.pathname !== next.pathname,
		withResolver: true,
		disabled: mode !== 'batch-modification',
	});
	const variable = useVariableConditions();
	const inlineVariableCondition = variable.length === 1 && variable[0]?.operator === 'equals' ? variable[0] : undefined;

	const optionalFilterValues = useMemo<OptionalFilterValues>(
		() => ({
			processInstanceKey,
			parentProcessInstanceKey,
			businessId,
			batchOperationKey,
			errorMessage,
			hasRetriesLeft,
			startDateFrom,
			startDateTo,
			endDateFrom,
			endDateTo,
		}),
		[
			processInstanceKey,
			parentProcessInstanceKey,
			businessId,
			batchOperationKey,
			errorMessage,
			hasRetriesLeft,
			startDateFrom,
			startDateTo,
			endDateFrom,
			endDateTo,
		],
	);

	const processItems = useMemo<ProcessItem[]>(() => {
		const seen = new Set<string>();
		const items = data.items.reduce<ProcessItem[]>((acc, def) => {
			if (!seen.has(def.processDefinitionId)) {
				seen.add(def.processDefinitionId);
				acc.push({id: def.processDefinitionId, label: def.name ?? def.processDefinitionId});
			}
			return acc;
		}, []);
		const selected = isDefinitionsReady
			? selectedDefinitions?.find(
					(definition) =>
						definition.processDefinitionId === process &&
						(specificTenantId === undefined || definition.tenantId === specificTenantId),
				)
			: undefined;
		if (selected !== undefined && !seen.has(selected.processDefinitionId)) {
			items.push({id: selected.processDefinitionId, label: selected.name ?? selected.processDefinitionId});
		}
		return items;
	}, [data, isDefinitionsReady, process, selectedDefinitions, specificTenantId]);

	const matchingDefinitions = useMemo(
		() =>
			isDefinitionsReady
				? (selectedDefinitions ?? []).filter(
						(definition) =>
							definition.processDefinitionId === process &&
							(specificTenantId === undefined || definition.tenantId === specificTenantId),
					)
				: [],
		[isDefinitionsReady, process, selectedDefinitions, specificTenantId],
	);

	const versionNumbers = useMemo<(number | 'all')[]>(() => {
		if (!isDefinitionsReady || matchingDefinitions.length === 0) {
			return [];
		}
		const versions = [...new Set(matchingDefinitions.map((def) => def.version))].sort((a, b) => b - a);
		return ['all', ...versions];
	}, [isDefinitionsReady, matchingDefinitions]);

	const selectedProcess = processItems.find((i) => i.id === process) ?? null;
	const selectedVersion = version ?? 'all';

	const processDefinitionSelection = useMemo<ProcessDefinitionSelection>(() => {
		if (!isDefinitionsReady) {
			return {kind: 'no-match'};
		}

		const definition =
			version === undefined
				? matchingDefinitions[0]
				: matchingDefinitions.find((candidate) => candidate.version === version);
		if (definition === undefined) {
			return {kind: 'no-match'};
		}
		const tenantCandidates =
			version === undefined
				? matchingDefinitions
				: matchingDefinitions.filter((candidate) => candidate.version === version);
		if (!specificTenantId && tenantCandidates.some((candidate) => candidate.tenantId !== definition.tenantId)) {
			return {
				kind: 'multiple-tenants',
				definition: {name: definition.name, processDefinitionId: definition.processDefinitionId},
			};
		}
		if (version === undefined) {
			return {
				kind: 'all-versions',
				definition: {name: definition.name, processDefinitionId: definition.processDefinitionId},
			};
		}

		return {kind: 'single-version', definition};
	}, [isDefinitionsReady, matchingDefinitions, specificTenantId, version]);
	const selectedDefinitionKey =
		processDefinitionSelection.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const {
		data: diagramData,
		error: xmlError,
		isError: isXmlError,
		refetch: refetchDiagramXml,
	} = useDiagramXml(selectedDefinitionKey);
	const elementItems = useMemo(
		() =>
			(diagramData?.selectableElements ?? [])
				.map((id) => ({id, label: diagramData?.businessObjects[id]?.name ?? id}))
				.sort((a, b) => {
					const label = a.label.toUpperCase();
					const nextLabel = b.label.toUpperCase();
					return label < nextLabel ? -1 : label > nextLabel ? 1 : 0;
				}),
		[diagramData],
	);
	const selectedElement = useMemo(
		() => elementItems.find((item) => item.id === elementId) ?? (elementId ? {id: elementId, label: elementId} : null),
		[elementItems, elementId],
	);
	const isElementDisabled =
		!isDefinitionsReady || selectedDefinitionKey === undefined || isXmlError || elementItems.length === 0;
	const notChangeableTitle = mode === 'list' ? undefined : t('operate.processes.batchModification.notChangeable');

	const runningChecked = active && incidents && suspended;
	const runningIndeterminate = !runningChecked && (active || incidents || suspended);
	const finishedChecked = completed && canceled;
	const finishedIndeterminate = !finishedChecked && (completed || canceled);

	const hasOptionalFilters =
		tenantId !== undefined ||
		incidentErrorHashCode !== undefined ||
		Object.values(optionalFilterValues).some((value) => value !== undefined);
	const hasVariableFilter = variable.length > 0;

	const isResetDisabled =
		active &&
		incidents &&
		suspended &&
		!completed &&
		!canceled &&
		!process &&
		version === undefined &&
		elementId === undefined &&
		!hasOptionalFilters &&
		!hasVariableFilter &&
		visibleFilters.length === 0;

	const handleFiltersSubmit = (values: FiltersFormValues) => {
		void navigate({
			to: '.',
			search: (prev) => ({
				...prev,
				...((values.tenantId || undefined) !== prev.tenantId
					? {process: undefined, version: undefined, elementId: undefined}
					: {}),
				tenantId: values.tenantId || undefined,
				processInstanceKey: values.processInstanceKey || undefined,
				parentProcessInstanceKey: values.parentProcessInstanceKey || undefined,
				businessId: values.businessId || undefined,
				batchOperationKey: values.batchOperationKey || undefined,
				errorMessage: values.errorMessage || undefined,
				incidentErrorHashCode: values.errorMessage === prev.errorMessage ? prev.incidentErrorHashCode : undefined,
				hasRetriesLeft: values.hasRetriesLeft || undefined,
				startDateFrom: values.startDateFrom || undefined,
				startDateTo: values.startDateTo || undefined,
				endDateFrom: values.endDateFrom || undefined,
				endDateTo: values.endDateTo || undefined,
			}),
		});
	};

	if (mode === 'migration' && migration !== null) {
		return (
			<MigrationView
				source={migration.source}
				scope={migration.scope}
				onExit={() => {
					setMigration(null);
					setMode('list');
				}}
			/>
		);
	}

	return (
		<ProcessesLayout
			type="process"
			frame={{
				isVisible: mode === 'batch-modification',
				headerTitle: t('operate.processes.batchModification.frameTitle'),
			}}
			leftPanel={
				<Form<FiltersFormValues>
					onSubmit={handleFiltersSubmit}
					initialValuesEqual={areUrlFilterValuesEqual}
					initialValues={{
						tenantId,
						...optionalFilterValues,
						variableName: inlineVariableCondition?.name,
						variableValues: inlineVariableCondition?.value,
					}}
				>
					{({handleSubmit, form}) => (
						<StyledForm onSubmit={handleSubmit}>
							<AutoSubmit fieldsToSkipTimeout={['tenantId', 'hasRetriesLeft']} />
							<FiltersPanel
								localStorageKey="isProcessesFiltersCollapsed"
								isResetButtonDisabled={isResetDisabled || mode !== 'list'}
								onResetClick={() => {
									setVariableConditions([]);
									form.reset();
									setVisibleFilters([]);
									void navigate({to: '.', search: {}});
								}}
							>
								<Stack gap={5}>
									{getClientConfig().deployment.isMultiTenancyEnabled && (
										<div>
											<Title>{t('operate.processes.filters.tenant')}</Title>
											{/* Like legacy, the tenant filter stays usable in the mode; only process, version and element lock. */}
											<TenantField />
										</div>
									)}
									<div>
										<Title>{t('operate.processes.filters.processSection')}</Title>
										<Stack gap={5}>
											<ComboBox
												id="process-name-filter"
												titleText={t('operate.processes.filters.name')}
												placeholder={t('operate.processes.filters.searchByName')}
												items={processItems}
												itemToString={(item) => item?.label ?? ''}
												selectedItem={selectedProcess}
												disabled={mode !== 'list'}
												title={notChangeableTitle}
												size="sm"
												onChange={({selectedItem}) => {
													if (selectedItem?.id === process) {
														return;
													}
													void navigate({
														to: '.',
														search: (prev) => ({
															...prev,
															process: selectedItem?.id,
															version: undefined,
															elementId: undefined,
														}),
													});
												}}
											/>
											<Dropdown
												key={process ? 'process-selected' : 'process-unselected'}
												id="process-version-filter"
												titleText={t('operate.processes.filters.version')}
												label={t('operate.processes.filters.selectVersion')}
												items={versionNumbers}
												itemToString={(item) =>
													item === 'all' || item === undefined || item === null
														? t('operate.processes.filters.allVersions')
														: String(item)
												}
												selectedItem={process ? selectedVersion : version}
												disabled={!isDefinitionsReady || versionNumbers.length === 0 || mode !== 'list'}
												size="sm"
												onChange={({selectedItem}) => {
													if (selectedItem === null || selectedItem === undefined) {
														return;
													}
													void navigate({
														to: '.',
														search: (prev) => ({
															...prev,
															version: selectedItem === 'all' ? undefined : selectedItem,
															elementId: undefined,
														}),
													});
												}}
											/>
											{isDefinitionsError && !isDefinitionsFetching && (
												<div role="alert">
													{selectedDefinitionsError instanceof ForbiddenError
														? t('operate.shared.diagramShell.forbiddenMessage')
														: t('operate.shared.errorMessage.message')}{' '}
													{!(selectedDefinitionsError instanceof ForbiddenError) && (
														<Button kind="ghost" size="sm" onClick={() => void refetchSelectedDefinitions()}>
															{t('operate.processes.filters.retryElementLoad')}
														</Button>
													)}
												</div>
											)}
											<ComboBox
												id="process-element-filter"
												titleText={t('operate.processes.filters.element')}
												placeholder={t('operate.processes.filters.searchByElement')}
												items={isXmlError ? [] : elementItems}
												itemToString={(item) => item?.label ?? ''}
												shouldFilterItem={({inputValue, item}) =>
													inputValue !== null && item.label.toLowerCase().includes(inputValue.toLowerCase())
												}
												selectedItem={selectedElement}
												disabled={isElementDisabled || mode !== 'list'}
												title={notChangeableTitle}
												size="sm"
												onChange={({selectedItem}) => {
													void navigate({
														to: '.',
														search: (prev) => ({...prev, elementId: selectedItem?.id}),
													});
												}}
											/>
											{isXmlError && selectedDefinitionKey !== undefined && (
												<div role="alert">
													{xmlError instanceof ForbiddenError
														? t('operate.shared.diagramShell.forbiddenMessage')
														: t('operate.shared.errorMessage.message')}{' '}
													{!(xmlError instanceof ForbiddenError) && (
														<Button kind="ghost" size="sm" onClick={() => void refetchDiagramXml()}>
															{t('operate.processes.filters.retryElementLoad')}
														</Button>
													)}
												</div>
											)}
											{isElementDisabled && elementId && (
												<Button
													kind="ghost"
													size="sm"
													onClick={() => {
														void navigate({to: '.', search: (prev) => ({...prev, elementId: undefined})});
													}}
												>
													{t('operate.processes.filters.clearElement')}
												</Button>
											)}
										</Stack>
									</div>
									<div>
										<Title>{t('operate.processes.filters.instancesStates')}</Title>
										<Stack gap={3}>
											<Stack gap={1}>
												<Checkbox
													id="filter-running-instances"
													labelText={t('operate.processes.filters.runningInstances')}
													checked={runningChecked}
													indeterminate={runningIndeterminate}
													onChange={(_, {checked}) => {
														void navigate({
															to: '.',
															search: (prev) => ({...prev, active: checked, incidents: checked, suspended: checked}),
														});
													}}
												/>
												<IndentedGroup>
													<Checkbox
														id="filter-active"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<RadioButtonChecked size={20} />
																<div>{t('operate.processes.filters.active')}</div>
															</Stack>
														}
														checked={active}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, active: checked})});
														}}
													/>
													<Checkbox
														id="filter-incidents"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<WarningFilled size={20} />
																<div>{t('operate.processes.filters.incidents')}</div>
															</Stack>
														}
														checked={incidents}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, incidents: checked})});
														}}
													/>
													<Checkbox
														id="filter-suspended"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<PauseOutlineFilled size={20} />
																<div>{t('operate.processes.filters.suspended')}</div>
															</Stack>
														}
														checked={suspended}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, suspended: checked})});
														}}
													/>
												</IndentedGroup>
											</Stack>
											<Stack gap={1}>
												<Checkbox
													id="filter-finished-instances"
													labelText={t('operate.processes.filters.finishedInstances')}
													checked={finishedChecked}
													indeterminate={finishedIndeterminate}
													onChange={(_, {checked}) => {
														void navigate({
															to: '.',
															search: (prev) => ({...prev, completed: checked, canceled: checked}),
														});
													}}
												/>
												<IndentedGroup>
													<Checkbox
														id="filter-completed"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<CheckmarkOutline size={20} />
																<div>{t('operate.processes.filters.completed')}</div>
															</Stack>
														}
														checked={completed}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, completed: checked})});
														}}
													/>
													<Checkbox
														id="filter-canceled"
														labelText={
															<Stack orientation="horizontal" gap={3}>
																<CanceledIcon size={20} />
																<div>{t('operate.processes.filters.canceled')}</div>
															</Stack>
														}
														checked={canceled}
														onChange={(_, {checked}) => {
															void navigate({to: '.', search: (prev) => ({...prev, canceled: checked})});
														}}
													/>
												</IndentedGroup>
											</Stack>
										</Stack>
									</div>
									<OptionalFiltersFormGroup
										filters={optionalFilterValues}
										visibleFilters={visibleFilters}
										onVisibleFilterChange={setVisibleFilters}
									/>
								</Stack>
							</FiltersPanel>
						</StyledForm>
					)}
				</Form>
			}
			topPanel={
				<DiagramPanel
					processDefinitionSelection={processDefinitionSelection}
					isDefinitionSelectionLoading={isDefinitionsLoading}
					isDefinitionSelectionError={isDefinitionsError}
					elementId={elementId}
					mode={mode}
					modificationScope={selectionScope ?? undefined}
					selectedTargetElementId={selectedTargetElementId}
					onTargetElementSelection={setSelectedTargetElementId}
					onElementSelection={(selectedElementId) => {
						void navigate({
							to: '.',
							search: (prev) => ({...prev, elementId: selectedElementId || undefined}),
						});
					}}
					active={active}
					incidents={incidents}
					completed={completed}
					canceled={canceled}
					suspended={suspended}
					variable={variable}
					otherFilters={{
						tenantId,
						businessId,
						processInstanceKey,
						parentProcessInstanceKey,
						batchOperationKey,
						errorMessage,
						incidentErrorHashCode,
						hasRetriesLeft,
						startDateFrom,
						startDateTo,
						endDateFrom,
						endDateTo,
					}}
				/>
			}
			bottomPanel={
				<InstancesTable
					mode={mode}
					navigationBlocker={blocker}
					processDefinitionSelection={processDefinitionSelection}
					selectedTargetElementId={selectedTargetElementId}
					onEnterMode={setMode}
					onMigrationEnter={
						ENABLE_PROCESS_MIGRATION
							? (source, scope) => {
									setMigration({source, scope});
									setMode('migration');
								}
							: undefined
					}
					onSelectionScopeChange={setSelectionScope}
					onExitMode={() => {
						blocker.proceed?.();
						setSelectedTargetElementId(undefined);
						setMode('list');
					}}
					search={{
						process,
						version,
						elementId,
						tenantId,
						processInstanceKey,
						parentProcessInstanceKey,
						businessId,
						batchOperationKey,
						errorMessage,
						variable,
						incidentErrorHashCode,
						hasRetriesLeft,
						startDateFrom,
						startDateTo,
						endDateFrom,
						endDateTo,
						active,
						incidents,
						completed,
						canceled,
						suspended,
						sort,
					}}
				/>
			}
		/>
	);
};

export {Processes};
