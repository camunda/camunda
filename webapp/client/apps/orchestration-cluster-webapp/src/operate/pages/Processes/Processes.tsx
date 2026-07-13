/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useNavigate} from '@tanstack/react-router';
import {Checkbox, ComboBox, Dropdown, Stack} from '@carbon/react';
import {queries} from '#/shared/http/queries';
import {InstancesList} from '#/operate/shared/InstancesList/InstancesList';
import {FiltersPanel} from '#/operate/shared/FiltersPanel/FiltersPanel';
import {Title} from '#/operate/shared/FiltersPanel/styled';
import {RadioButtonChecked, WarningFilled, CheckmarkOutline} from '#/operate/shared/StateIcon/styled';
import {IndentedGroup, CanceledIcon} from './styled';

type Props = {
	process?: string;
	version?: number;
	elementId?: string;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
};

type ProcessItem = {id: string; label: string};

const Processes: React.FC<Props> = ({process, version, active, incidents, completed, canceled}) => {
	const navigate = useNavigate();
	const {data} = useSuspenseQuery(queries.queryProcessDefinitions({}));

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
	const selectedVersion = version ?? undefined;

	const runningChecked = active && incidents;
	const runningIndeterminate = !runningChecked && (active || incidents);
	const finishedChecked = completed && canceled;
	const finishedIndeterminate = !finishedChecked && (completed || canceled);

	const isResetDisabled = active && incidents && !completed && !canceled && !process;

	return (
		<InstancesList
			type="process"
			leftPanel={
				<FiltersPanel
					localStorageKey="isProcessesFiltersCollapsed"
					isResetButtonDisabled={isResetDisabled}
					onResetClick={() => void navigate({to: '.', search: {}})}
				>
					<Stack gap={5}>
						<div>
							<Title>Process</Title>
							<Stack gap={5}>
								<ComboBox
									id="process-name-filter"
									titleText="Name"
									placeholder="Search by Process Name"
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
									titleText="Version"
									label="Select a Process Version"
									items={versionNumbers}
									itemToString={(item) => (item === undefined || item === null ? 'All versions' : String(item))}
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
									titleText="Element"
									placeholder="Search by Process Element"
									items={[]}
									itemToString={(item: {label?: string} | null) => item?.label ?? ''}
									selectedItem={null}
									disabled={!process}
									size="sm"
									onChange={() => {}}
								/>
							</Stack>
						</div>
						<div>
							<Title>Instances States</Title>
							<Stack gap={3}>
								<Stack gap={1}>
									<Checkbox
										id="filter-running-instances"
										labelText="Running Instances"
										checked={runningChecked}
										indeterminate={runningIndeterminate}
										onChange={(_, {checked}) => {
											void navigate({to: '.', search: (prev) => ({...prev, active: checked, incidents: checked})});
										}}
									/>
									<IndentedGroup>
										<Checkbox
											id="filter-active"
											labelText={
												<Stack orientation="horizontal" gap={3}>
													<RadioButtonChecked size={20} />
													<div>Active</div>
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
													<div>Incidents</div>
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
										labelText="Finished Instances"
										checked={finishedChecked}
										indeterminate={finishedIndeterminate}
										onChange={(_, {checked}) => {
											void navigate({to: '.', search: (prev) => ({...prev, completed: checked, canceled: checked})});
										}}
									/>
									<IndentedGroup>
										<Checkbox
											id="filter-completed"
											labelText={
												<Stack orientation="horizontal" gap={3}>
													<CheckmarkOutline size={20} />
													<div>Completed</div>
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
													<div>Canceled</div>
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
					</Stack>
				</FiltersPanel>
			}
			topPanel={<div />}
			bottomPanel={<div />}
		/>
	);
};

export {Processes};
