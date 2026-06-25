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
import {useTranslation} from 'react-i18next';
import {Checkbox, ComboBox, Dropdown} from '@carbon/react';
import {queries} from '#/shared/http/queries';
import {InstancesList} from '#/operate/shared/InstancesList';
import {FiltersPanel} from '#/operate/shared/FiltersPanel';

type Props = {
	process?: string;
	version?: number;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
};

type ProcessItem = {id: string; label: string};
type VersionItem = {id: number | undefined; label: string};

const Processes: React.FC<Props> = ({process, version, active, incidents, completed, canceled}) => {
	const {t} = useTranslation();
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

	const versionItems = useMemo<VersionItem[]>(() => {
		if (!process) {
			return [];
		}
		const all: VersionItem = {id: undefined, label: t('operate.processes.filters.allVersions')};
		const versions: VersionItem[] = data.items
			.filter((def) => def.processDefinitionId === process)
			.sort((a, b) => b.version - a.version)
			.map((def) => ({
				id: def.version,
				label: t('operate.dashboard.version', {version: def.version}),
			}));
		return [all, ...versions];
	}, [data, process, t]);

	const selectedProcess = processItems.find((i) => i.id === process) ?? null;
	const selectedVersion = versionItems.find((i) => i.id === version) ?? versionItems[0] ?? null;

	const isResetDisabled = !process && !active && !incidents && !completed && !canceled;

	return (
		<InstancesList
			type="process"
			leftPanel={
				<FiltersPanel
					localStorageKey="isProcessesFiltersCollapsed"
					isResetButtonDisabled={isResetDisabled}
					onResetClick={() => void navigate({to: '.', search: {}})}
				>
					<ComboBox
						id="process-name-filter"
						titleText={t('operate.processes.filters.name')}
						items={processItems}
						itemToString={(item) => item?.label ?? ''}
						selectedItem={selectedProcess}
						onChange={({selectedItem}) => {
							void navigate({
								to: '.',
								search: (prev) => ({...prev, process: selectedItem?.id, version: undefined}),
							});
						}}
					/>
					{process && (
						<Dropdown
							id="process-version-filter"
							titleText={t('operate.processes.filters.version')}
							label=""
							items={versionItems}
							itemToString={(item) => item?.label ?? ''}
							selectedItem={selectedVersion}
							onChange={({selectedItem}) => {
								void navigate({
									to: '.',
									search: (prev) => ({...prev, version: selectedItem?.id}),
								});
							}}
						/>
					)}
					<Checkbox
						id="filter-active"
						labelText={t('operate.processes.filters.active')}
						checked={active}
						onChange={(_, {checked}) => {
							void navigate({to: '.', search: (prev) => ({...prev, active: checked})});
						}}
					/>
					<Checkbox
						id="filter-incidents"
						labelText={t('operate.processes.filters.incidents')}
						checked={incidents}
						onChange={(_, {checked}) => {
							void navigate({to: '.', search: (prev) => ({...prev, incidents: checked})});
						}}
					/>
					<Checkbox
						id="filter-completed"
						labelText={t('operate.processes.filters.completed')}
						checked={completed}
						onChange={(_, {checked}) => {
							void navigate({to: '.', search: (prev) => ({...prev, completed: checked})});
						}}
					/>
					<Checkbox
						id="filter-canceled"
						labelText={t('operate.processes.filters.canceled')}
						checked={canceled}
						onChange={(_, {checked}) => {
							void navigate({to: '.', search: (prev) => ({...prev, canceled: checked})});
						}}
					/>
				</FiltersPanel>
			}
			topPanel={<div />}
			bottomPanel={<div />}
		/>
	);
};

export {Processes};
