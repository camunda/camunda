/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {Form} from 'react-final-form';
import {Checkbox, ComboBox, Dropdown, Stack} from '@carbon/react';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {InstancesList} from '#/operate/shared/InstancesList/InstancesList';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/FiltersPanel';
import {Title, Form as StyledForm} from '#/operate/shared/FiltersPanel/styled';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {TenantField} from '#/operate/shared/TenantField/TenantField';
import {RadioButtonChecked, WarningFilled, CheckmarkOutline} from '#/operate/shared/StateIcon/styled';
import {IndentedGroup, CanceledIcon} from './styled';
import {OptionalFiltersFormGroup, type OptionalFilter, type OptionalFilterValues} from './OptionalFiltersFormGroup';
import {DiagramPanel, type ProcessDefinitionSelection} from './DiagramPanel';

type FiltersFormValues = OptionalFilterValues & {tenantId?: string};

type Props = {
	process?: string;
	version?: number;
	elementId?: string;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
} & FiltersFormValues;

type ProcessItem = {id: string; label: string};

const Processes: React.FC<Props> = ({
	process,
	version,
	elementId,
	active,
	incidents,
	completed,
	canceled,
	tenantId,
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
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {data} = useSuspenseQuery(queries.queryProcessDefinitions({page: {limit: 1000}}));
	const [visibleFilters, setVisibleFilters] = useState<OptionalFilter[]>([]);

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
		return data.items.reduce<ProcessItem[]>((acc, def) => {
			if (!seen.has(def.processDefinitionId)) {
				seen.add(def.processDefinitionId);
				acc.push({id: def.processDefinitionId, label: def.name ?? def.processDefinitionId});
			}
			return acc;
		}, []);
	}, [data]);

	const versionNumbers = useMemo<(number | undefined)[]>(() => {
		if (!process) {
			return [];
		}
		const versions = data.items
			.filter((def) => def.processDefinitionId === process)
			.sort((a, b) => b.version - a.version)
			.map((def) => def.version);
		return [undefined, ...versions];
	}, [data, process]);

	const selectedProcess = processItems.find((i) => i.id === process) ?? null;
	const selectedVersion = version;

	const processDefinitionSelection = useMemo<ProcessDefinitionSelection>(() => {
		if (!process) {
			return {kind: 'no-match'};
		}

		const matches = data.items.filter((def) => def.processDefinitionId === process);

		if (version === undefined) {
			const first = matches[0];
			return first === undefined
				? {kind: 'no-match'}
				: {kind: 'all-versions', definition: {name: first.name, processDefinitionId: first.processDefinitionId}};
		}

		const definition = matches.find((def) => def.version === version);
		return definition === undefined ? {kind: 'no-match'} : {kind: 'single-version', definition};
	}, [data, process, version]);

	const runningChecked = active && incidents;
	const runningIndeterminate = !runningChecked && (active || incidents);
	const finishedChecked = completed && canceled;
	const finishedIndeterminate = !finishedChecked && (completed || canceled);

	const hasOptionalFilters =
		tenantId !== undefined || Object.values(optionalFilterValues).some((value) => value !== undefined);

	const isResetDisabled =
		active &&
		incidents &&
		!completed &&
		!canceled &&
		!process &&
		version === undefined &&
		elementId === undefined &&
		!hasOptionalFilters &&
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
				hasRetriesLeft: values.hasRetriesLeft || undefined,
				startDateFrom: values.startDateFrom || undefined,
				startDateTo: values.startDateTo || undefined,
				endDateFrom: values.endDateFrom || undefined,
				endDateTo: values.endDateTo || undefined,
			}),
		});
	};

	return (
		<InstancesList
			type="process"
			leftPanel={
				<Form<FiltersFormValues> onSubmit={handleFiltersSubmit} initialValues={{tenantId, ...optionalFilterValues}}>
					{({handleSubmit, form}) => (
						<StyledForm onSubmit={handleSubmit}>
							<AutoSubmit fieldsToSkipTimeout={['tenantId', 'hasRetriesLeft']} />
							<FiltersPanel
								localStorageKey="isProcessesFiltersCollapsed"
								isResetButtonDisabled={isResetDisabled}
								onResetClick={() => {
									form.reset();
									setVisibleFilters([]);
									void navigate({to: '.', search: {}});
								}}
							>
								<Stack gap={5}>
									{getClientConfig().deployment.isMultiTenancyEnabled && (
										<div>
											<Title>{t('operate.processes.filters.tenant')}</Title>
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
												size="sm"
												onChange={({selectedItem}) => {
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
												id="process-version-filter"
												titleText={t('operate.processes.filters.version')}
												label={t('operate.processes.filters.selectVersion')}
												items={versionNumbers}
												itemToString={(item) =>
													item === undefined || item === null
														? t('operate.processes.filters.allVersions')
														: String(item)
												}
												selectedItem={selectedVersion}
												disabled={!process}
												size="sm"
												onChange={({selectedItem}) => {
													void navigate({
														to: '.',
														search: (prev) => ({...prev, version: selectedItem ?? undefined, elementId: undefined}),
													});
												}}
											/>
											<ComboBox
												id="process-element-filter"
												titleText={t('operate.processes.filters.element')}
												placeholder={t('operate.processes.filters.searchByElement')}
												items={[]}
												itemToString={(item: {label?: string} | null) => item?.label ?? ''}
												selectedItem={null}
												disabled
												size="sm"
												onChange={() => {}}
											/>
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
															search: (prev) => ({...prev, active: checked, incidents: checked}),
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
					elementId={elementId}
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
				/>
			}
			bottomPanel={<div />}
		/>
	);
};

export {Processes};
