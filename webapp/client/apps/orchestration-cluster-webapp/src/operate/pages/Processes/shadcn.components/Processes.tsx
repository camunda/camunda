/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import isEqual from 'lodash/isEqual';
import type {Decorator, FormApi} from 'final-form';
import {useTranslation} from 'react-i18next';
import {useBlocker, useLocation, useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Heading, PageLayout, Separator} from '@camunda/design-system';
import {FilterSidebar} from '#/operate/shared/FilterSidebar/shadcn.components/FilterSidebar';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {Frame} from '#/operate/shared/Frame/shadcn.components/Frame';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {processesSearchSchema, type ProcessesSearch} from '../processesFilter';
import type {BatchModificationScope} from '../useBatchModificationStatistics';
import {setVariableConditions, useVariableConditions} from '../VariablesFilter/variableFilterStore';
import type {OptionalFilter, OptionalFilterValues} from './optionalFilters';
import {OptionalFiltersFormGroup} from './OptionalFiltersFormGroup';
import {ProcessFilters} from './ProcessFilters';
import {InstancesTable} from './InstancesTable';
import {DiagramPanel} from './DiagramPanel';
import {useProcessFilterOptions} from './useProcessFilterOptions';
import {MigrationView} from './MigrationView';
import {getBatchModificationLockedFilterValues} from './batchModificationLockedFilters';
import {ENABLE_PROCESS_MIGRATION} from '#/shared/feature-flags';
import {getInstanceMigration} from '../instanceMigration';
import type {ProcessesMode} from './processesMode';

type FilterFormValues = OptionalFilterValues & {tenantId?: string};

type Props = {
	search: ProcessesSearch;
};

function isHistorySearchAction(action: string) {
	return action === 'BACK' || action === 'FORWARD' || action === 'GO';
}

function normalizeLockedFilterValue(value: unknown): string | undefined {
	if (value === undefined || value === null || value === '') {
		return undefined;
	}

	return String(value);
}

function normalizeLockedFilterValues(search: Record<string, unknown>) {
	const values = getBatchModificationLockedFilterValues(search);
	return {
		process: normalizeLockedFilterValue(values.process),
		version: normalizeLockedFilterValue(values.version),
		elementId: normalizeLockedFilterValue(values.elementId),
	};
}

function parseProcessesSearch(rawSearch: Record<string, unknown>) {
	const parsedSearch = {
		process: rawSearch['process'],
		version: rawSearch['version'],
		processDefinitionId: rawSearch['processDefinitionId'],
		processDefinitionVersion: rawSearch['processDefinitionVersion'],
		elementId: rawSearch['elementId'],
	};
	const result = processesSearchSchema.safeParse(parsedSearch);
	return result.success ? result.data : rawSearch;
}

function normalizeProcessesSearch(search: unknown): Record<string, unknown> {
	if (!search || typeof search !== 'object') {
		return {};
	}

	return parseProcessesSearch(search as Record<string, unknown>);
}

const Processes: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
	const instanceMigrationState = useLocation({select: ({state}) => state.operateInstanceMigration});
	const [migration, setMigration] = useState(() =>
		ENABLE_PROCESS_MIGRATION ? getInstanceMigration(instanceMigrationState) : null,
	);
	const preservingFormRef = useRef<FormApi<FilterFormValues> | null>(null);
	const filterFormRef = useRef<FormApi<FilterFormValues> | null>(null);
	const filterFormDecorators = useMemo<Decorator<FilterFormValues>[]>(
		() => [
			(form) => {
				filterFormRef.current = form;
				return () => {
					filterFormRef.current = null;
				};
			},
		],
		[],
	);
	const [mode, setMode] = useState<ProcessesMode>('list');
	const [selectedTargetElementId, setSelectedTargetElementId] = useState<string>();
	const [selectionScope, setSelectionScope] = useState<BatchModificationScope | null>(null);
	const [footerSlot, setFooterSlot] = useState<HTMLDivElement | null>(null);
	const isBatchModification = mode === 'batch-modification';
	const blocker = useBlocker({
		shouldBlockFn: ({current, next, action}) => {
			if (current.routeId !== next.routeId || current.pathname !== next.pathname) {
				return true;
			}

			if (!isHistorySearchAction(action)) {
				return false;
			}

			const currentSearch = normalizeProcessesSearch(current.search);
			const nextSearch = normalizeProcessesSearch(next.search);
			const currentLockedFilters = normalizeLockedFilterValues(currentSearch);
			const nextLockedFilters = normalizeLockedFilterValues(nextSearch);
			return !isEqual(currentLockedFilters, nextLockedFilters);
		},
		withResolver: true,
		disabled: !isBatchModification,
	});
	const variable = useVariableConditions();
	const filterOptions = useProcessFilterOptions({
		process: search.process,
		version: search.version,
		elementId: search.elementId,
		tenantId: search.tenantId,
	});
	const instancesSearch = useMemo<ProcessesSearch>(() => ({...search, variable}), [search, variable]);
	const {
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
	} = search;
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
	const filterFormValues = useMemo<FilterFormValues>(
		() => ({
			tenantId: search.tenantId,
			...optionalFilterValues,
		}),
		[search.tenantId, optionalFilterValues],
	);
	const hasOptionalFilters =
		search.tenantId !== undefined ||
		search.incidentErrorHashCode !== undefined ||
		Object.values(optionalFilterValues).some((value) => value !== undefined);
	const isResetDisabled =
		isBatchModification ||
		(search.active &&
			search.incidents &&
			search.suspended &&
			!search.completed &&
			!search.canceled &&
			!search.process &&
			search.version === undefined &&
			search.elementId === undefined &&
			!hasOptionalFilters &&
			variable.length === 0 &&
			visibleFilters.length === 0);
	const clearFilters = () => {
		filterFormRef.current?.reset();
		setVariableConditions([]);
		setVisibleFilters([]);
		void navigate({to: '.', search: {active: true, suspended: true, incidents: true}});
	};

	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelMinHeight = clientHeight / 4;

	const isMigrating = migration !== null;

	useEffect(() => {
		if (!isMigrating) {
			setClientHeight(containerRef.current?.clientHeight ?? 0);
		}
	}, [isMigrating]);

	useEffect(() => {
		if (instanceMigrationState !== undefined) {
			void navigate({
				to: '.',
				search: true,
				hash: true,
				state: ({operateInstanceMigration: _, ...state}) => state,
				replace: true,
				ignoreBlocker: true,
			});
		}
	}, [instanceMigrationState, navigate]);

	useEffect(() => {
		preservingFormRef.current?.setConfig('keepDirtyOnReinitialize', false);
		preservingFormRef.current = null;
	}, [optionalFilterValues]);

	if (migration !== null) {
		return (
			<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
				<MigrationView source={migration.source} scope={migration.scope} onExit={() => setMigration(null)} />
			</PageLayout>
		);
	}

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<Frame
				frame={{isVisible: isBatchModification, headerTitle: t('operate.processes.batchModification.frameTitleDs')}}
			>
				<div className="flex h-full min-h-0 flex-1 overflow-hidden">
					<Heading as="h1" className="sr-only">
						{t('operate.processes.title')}
					</Heading>
					<Form<FilterFormValues>
						decorators={filterFormDecorators}
						onSubmit={(values) => {
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
									incidentErrorHashCode:
										values.errorMessage === prev.errorMessage ? prev.incidentErrorHashCode : undefined,
									hasRetriesLeft: values.hasRetriesLeft || undefined,
									startDateFrom: values.startDateFrom || undefined,
									startDateTo: values.startDateTo || undefined,
									endDateFrom: values.endDateFrom || undefined,
									endDateTo: values.endDateTo || undefined,
								}),
							});
						}}
						initialValues={filterFormValues}
					>
						{({handleSubmit, form}) => (
							<form onSubmit={handleSubmit} className="flex h-full min-h-0">
								<AutoSubmit fieldsToSkipTimeout={['tenantId', 'hasRetriesLeft']} />
								<FilterSidebar
									hideCollapsedLabel
									localStorageKey="isProcessesFiltersCollapsed"
									isResetButtonDisabled={isResetDisabled}
									onResetClick={clearFilters}
								>
									<div className="flex flex-col gap-8">
										<ProcessFilters
											process={search.process}
											version={search.version}
											elementId={search.elementId}
											options={filterOptions}
											isLocked={isBatchModification}
											tenantId={search.tenantId}
											active={search.active}
											incidents={search.incidents}
											suspended={search.suspended}
											completed={search.completed}
											canceled={search.canceled}
										/>
										<Separator />
										<OptionalFiltersFormGroup
											filters={optionalFilterValues}
											visibleFilters={visibleFilters}
											onVisibleFilterChange={setVisibleFilters}
											onFilterClear={(keys) => {
												if (
													Object.entries(optionalFilterValues).some(
														([key, value]) => keys.includes(key) && value !== undefined,
													)
												) {
													form.setConfig('keepDirtyOnReinitialize', true);
													preservingFormRef.current = form;
												}
												void navigate({
													to: '.',
													search: (prev) => ({...prev, ...Object.fromEntries(keys.map((key) => [key, undefined]))}),
												});
											}}
										/>
									</div>
								</FilterSidebar>
							</form>
						)}
					</Form>
					<div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
						<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
							<ResizablePanel
								panelId="process-instances-vertical-panel"
								direction={SplitDirection.Vertical}
								minHeights={[panelMinHeight, panelMinHeight]}
							>
								<DiagramPanel
									processDefinitionSelection={filterOptions.processDefinitionSelection}
									isDefinitionSelectionLoading={filterOptions.areDefinitionsLoading}
									isDefinitionSelectionError={filterOptions.areDefinitionsError}
									search={instancesSearch}
									mode={mode}
									selectedTargetElementId={selectedTargetElementId}
									onTargetElementSelection={setSelectedTargetElementId}
									modificationScope={selectionScope ?? undefined}
									onElementSelection={(elementId) => {
										void navigate({to: '.', search: (prev) => ({...prev, elementId: elementId || undefined})});
									}}
								/>
								<div className="h-full overflow-hidden">
									<InstancesTable
										search={instancesSearch}
										mode={mode}
										navigationBlocker={blocker}
										processDefinitionSelection={filterOptions.processDefinitionSelection}
										isDefinitionSelectionPlaceholder={filterOptions.areDefinitionsPlaceholder}
										selectedTargetElementId={selectedTargetElementId}
										footerContainer={footerSlot}
										onMigrationEnter={(source, scope) => setMigration({source, scope})}
										onClearFilters={isResetDisabled ? undefined : clearFilters}
										onEnterMode={() => setMode('batch-modification')}
										onSelectionScopeChange={setSelectionScope}
										onExitMode={() => {
											blocker.proceed?.();
											setSelectedTargetElementId(undefined);
											setMode('list');
										}}
									/>
								</div>
							</ResizablePanel>
						</div>
						<div ref={setFooterSlot} />
					</div>
				</div>
			</Frame>
		</PageLayout>
	);
};

export {Processes};
