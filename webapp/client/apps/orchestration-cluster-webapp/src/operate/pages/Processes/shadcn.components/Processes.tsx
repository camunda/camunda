/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import type {FormApi} from 'final-form';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Heading, PageLayout, Separator, Text} from '@camunda/design-system';
import {FilterSidebar} from '#/operate/shared/FilterSidebar/shadcn.components/FilterSidebar';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import type {ProcessesSearch} from '../processesFilter';
import {setVariableConditions, useVariableConditions} from '../VariablesFilter/variableFilterStore';
import type {OptionalFilter, OptionalFilterValues, VariableFieldValues} from './optionalFilters';
import {OptionalFiltersFormGroup} from './OptionalFiltersFormGroup';
import {ProcessFilters} from './ProcessFilters';

type FilterFormValues = OptionalFilterValues & VariableFieldValues & {tenantId?: string};

const SESSION_FILTER_FIELDS = new Set(['variableName', 'variableValues']);

// The variable fields follow session storage; re-initialising on their change would discard unsubmitted URL filter edits.
function areUrlFilterValuesEqual(previous: Record<string, unknown> = {}, next: Record<string, unknown> = {}) {
	const keys = new Set([...Object.keys(previous), ...Object.keys(next)]);
	return [...keys].every((key) => SESSION_FILTER_FIELDS.has(key) || previous[key] === next[key]);
}

type Props = {
	search: ProcessesSearch;
};

const Processes: React.FC<Props> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);
	const preservingFormRef = useRef<FormApi<FilterFormValues> | null>(null);
	const variable = useVariableConditions();
	const inlineVariableCondition = variable.length === 1 && variable[0]?.operator === 'equals' ? variable[0] : undefined;
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
			variableName: inlineVariableCondition?.name,
			variableValues: inlineVariableCondition?.value,
		}),
		[search.tenantId, optionalFilterValues, inlineVariableCondition?.name, inlineVariableCondition?.value],
	);
	const hasOptionalFilters =
		search.tenantId !== undefined ||
		search.incidentErrorHashCode !== undefined ||
		Object.values(optionalFilterValues).some((value) => value !== undefined);
	const isResetDisabled =
		search.active &&
		search.incidents &&
		search.suspended &&
		!search.completed &&
		!search.canceled &&
		!search.process &&
		search.version === undefined &&
		search.elementId === undefined &&
		!hasOptionalFilters &&
		variable.length === 0 &&
		visibleFilters.length === 0;

	const containerRef = useRef<HTMLDivElement>(null);
	const [clientHeight, setClientHeight] = useState(0);
	const panelMinHeight = clientHeight / 4;

	useEffect(() => {
		setClientHeight(containerRef.current?.clientHeight ?? 0);
	}, []);

	useEffect(() => {
		preservingFormRef.current?.setConfig('keepDirtyOnReinitialize', false);
		preservingFormRef.current = null;
	}, [optionalFilterValues]);

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 overflow-hidden">
				<Heading as="h1" className="sr-only">
					{t('operate.processes.title')}
				</Heading>
				<Form<FilterFormValues>
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
					initialValuesEqual={areUrlFilterValuesEqual}
				>
					{({handleSubmit, form}) => (
						<form onSubmit={handleSubmit} className="flex h-full min-h-0">
							<AutoSubmit fieldsToSkipTimeout={['tenantId', 'hasRetriesLeft']} />
							<FilterSidebar
								hideCollapsedLabel
								localStorageKey="isProcessesFiltersCollapsed"
								isResetButtonDisabled={isResetDisabled}
								onResetClick={() => {
									setVariableConditions([]);
									form.reset();
									setVisibleFilters([]);
									void navigate({to: '.', search: {active: true, suspended: true, incidents: true}});
								}}
							>
								<div className="flex flex-col gap-8">
									<ProcessFilters
										process={search.process}
										version={search.version}
										elementId={search.elementId}
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
				<div ref={containerRef} className="min-h-0 min-w-0 flex-1 overflow-hidden">
					<ResizablePanel
						panelId="process-instances-vertical-panel"
						direction={SplitDirection.Vertical}
						minHeights={[panelMinHeight, panelMinHeight]}
					>
						<section className="flex h-full flex-col">
							<PanelHeader title={t('operate.processes.diagramHeader.title')} className="bg-transparent" />
							<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
								{t('operate.processes.scaffold.diagramPanelPlaceholder')}
							</Text>
						</section>
						<section className="flex h-full flex-col">
							<PanelHeader title={t('operate.processes.instancesTable.title')} className="bg-transparent" />
							<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
								{t('operate.processes.scaffold.instancesTablePlaceholder')}
							</Text>
						</section>
					</ResizablePanel>
				</div>
			</div>
		</PageLayout>
	);
};

export {Processes};
