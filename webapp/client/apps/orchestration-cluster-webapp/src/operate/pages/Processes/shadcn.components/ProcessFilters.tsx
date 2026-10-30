/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {
	Button,
	Checkbox,
	Combobox,
	Heading,
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import {ForbiddenError} from '#/shared/errors';
import {TenantField} from '#/operate/shared/TenantField/shadcn.components/TenantField';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';
import type {ProcessesSearch} from '../processesFilter';
import type {ProcessFilterOptions} from './useProcessFilterOptions';

const ALL_VERSIONS = 'all';

type Props = Pick<
	ProcessesSearch,
	'process' | 'version' | 'elementId' | 'tenantId' | 'active' | 'incidents' | 'suspended' | 'completed' | 'canceled'
> & {
	options: ProcessFilterOptions;
};

const toCheckedState = (all: boolean, some: boolean) => (all ? true : some ? 'indeterminate' : false);

type LoadErrorProps = {
	error: unknown;
	onRetry: () => void;
};

const LoadError: React.FC<LoadErrorProps> = ({error, onRetry}) => {
	const {t} = useTranslation();

	return (
		<div role="alert" className="text-sm text-danger-foreground-subtle">
			{error instanceof ForbiddenError
				? t('operate.shared.diagramShell.forbiddenMessage')
				: t('operate.shared.errorMessage.message')}{' '}
			{!(error instanceof ForbiddenError) && (
				<Button variant="ghost" size="sm" onClick={onRetry}>
					{t('operate.processes.filters.retryElementLoad')}
				</Button>
			)}
		</div>
	);
};

type StateCheckboxProps = {
	id: string;
	label: string;
	checked: boolean | 'indeterminate';
	icon: React.ReactNode;
	onChange: (checked: boolean) => void;
};

const StateCheckbox: React.FC<StateCheckboxProps> = ({id, label, checked, icon, onChange}) => (
	<Label htmlFor={id} className="cursor-pointer font-normal">
		<Checkbox id={id} checked={checked} onCheckedChange={(next) => onChange(next === true)} />
		{icon}
		{label}
	</Label>
);

const ProcessFilters: React.FC<Props> = ({
	process,
	version,
	elementId,
	tenantId,
	options,
	active,
	incidents,
	suspended,
	completed,
	canceled,
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {
		selectProcess,
		selectVersion,
		selectElement,
		processItems,
		selectedProcess,
		versionNumbers,
		areDefinitionsReady,
		areDefinitionsError,
		areDefinitionsFetching,
		selectedDefinitionsError,
		refetchSelectedDefinitions,
		selectedDefinitionKey,
		elementItems,
		selectedElement,
		isElementDisabled,
		isXmlError,
		xmlError,
		refetchDiagramXml,
		isMultiTenancyEnabled,
	} = options;
	const selectedVersion = process ? String(version ?? ALL_VERSIONS) : '';
	const isRunningAll = active && incidents && suspended;
	const isFinishedAll = completed && canceled;

	const processOptions = useMemo(
		() =>
			(selectedProcess !== null && !processItems.some(({id}) => id === selectedProcess.id)
				? [...processItems, selectedProcess]
				: processItems
			).map(({id, label}) => ({value: id, label})),
		[processItems, selectedProcess],
	);

	const elementOptions = useMemo(
		() =>
			(selectedElement !== null && !elementItems.some(({id}) => id === selectedElement.id)
				? [...elementItems, selectedElement]
				: elementItems
			).map(({id, label}) => ({value: id, label})),
		[elementItems, selectedElement],
	);

	return (
		<div className="flex flex-col gap-8">
			<div className="flex flex-col gap-5">
				{isMultiTenancyEnabled && (
					<section className="flex flex-col gap-3">
						<Heading as="h3" variant="heading-xs">
							{t('operate.processes.filters.tenant')}
						</Heading>
						<TenantField />
					</section>
				)}
				<section className="flex flex-col gap-3">
					<Heading as="h3" variant="heading-xs">
						{t('operate.processes.filters.processSection')}
					</Heading>
					<div className="flex flex-col gap-1.5">
						<Label htmlFor="process-name-filter">{t('operate.processes.filters.name')}</Label>
						<Combobox
							id="process-name-filter"
							className="w-full"
							size="sm"
							options={processOptions}
							value={selectedProcess?.id ?? ''}
							disabled={isMultiTenancyEnabled && tenantId === undefined}
							placeholder={t('operate.processes.ds.filters.searchByName')}
							searchPlaceholder={t('operate.processes.ds.filters.searchByName')}
							onValueChange={selectProcess}
						/>
					</div>
					<div className="flex flex-col gap-1.5">
						<Label htmlFor="process-version-filter">{t('operate.processes.filters.version')}</Label>
						<Select
							value={selectedVersion}
							disabled={!areDefinitionsReady || versionNumbers.length === 0}
							onValueChange={selectVersion}
						>
							<SelectTrigger id="process-version-filter" size="sm" className="w-full">
								<SelectValue placeholder={t('operate.processes.ds.filters.selectVersion')} />
							</SelectTrigger>
							<SelectContent>
								{versionNumbers.map((item) => (
									<SelectItem key={item} value={String(item)}>
										{item === ALL_VERSIONS ? t('operate.processes.filters.allVersions') : item}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
					{areDefinitionsError && !areDefinitionsFetching && (
						<LoadError error={selectedDefinitionsError} onRetry={() => void refetchSelectedDefinitions()} />
					)}
					<div className="flex flex-col gap-1.5">
						<Label htmlFor="process-element-filter">{t('operate.processes.filters.element')}</Label>
						<Combobox
							id="process-element-filter"
							className="w-full"
							size="sm"
							options={elementOptions}
							value={selectedElement?.id ?? ''}
							disabled={isElementDisabled}
							placeholder={t('operate.processes.ds.filters.searchByElement')}
							searchPlaceholder={t('operate.processes.ds.filters.searchByElement')}
							onValueChange={selectElement}
						/>
					</div>
					{isXmlError && selectedDefinitionKey !== undefined && (
						<LoadError error={xmlError} onRetry={() => void refetchDiagramXml()} />
					)}
					{isElementDisabled && elementId && (
						<Button variant="ghost" size="sm" onClick={() => selectElement(undefined)}>
							{t('operate.processes.filters.clearElement')}
						</Button>
					)}
				</section>
			</div>
			<section className="flex flex-col gap-3">
				<Heading as="h3" variant="heading-xs">
					{t('operate.processes.filters.instancesStates')}
				</Heading>
				<div className="flex flex-col gap-2">
					<Label htmlFor="filter-running-instances" className="cursor-pointer font-normal">
						<Checkbox
							id="filter-running-instances"
							checked={toCheckedState(isRunningAll, active || incidents || suspended)}
							onCheckedChange={(checked) => {
								void navigate({
									to: '.',
									search: (prev) => ({
										...prev,
										active: checked === true,
										incidents: checked === true,
										suspended: checked === true,
									}),
								});
							}}
						/>
						{t('operate.processes.filters.runningInstances')}
					</Label>
					<div className="ml-6 flex flex-col gap-2">
						<StateCheckbox
							id="filter-active"
							label={t('operate.processes.filters.active')}
							checked={active}
							icon={<StateIcon state="ACTIVE" size={16} />}
							onChange={(checked) => void navigate({to: '.', search: (prev) => ({...prev, active: checked})})}
						/>
						<StateCheckbox
							id="filter-incidents"
							label={t('operate.processes.filters.incidents')}
							checked={incidents}
							icon={<StateIcon state="INCIDENT" size={16} />}
							onChange={(checked) => void navigate({to: '.', search: (prev) => ({...prev, incidents: checked})})}
						/>
						<StateCheckbox
							id="filter-suspended"
							label={t('operate.processes.filters.suspended')}
							checked={suspended}
							icon={<StateIcon state="SUSPENDED" size={16} />}
							onChange={(checked) => void navigate({to: '.', search: (prev) => ({...prev, suspended: checked})})}
						/>
					</div>
					<Label htmlFor="filter-finished-instances" className="cursor-pointer font-normal">
						<Checkbox
							id="filter-finished-instances"
							checked={toCheckedState(isFinishedAll, completed || canceled)}
							onCheckedChange={(checked) => {
								void navigate({
									to: '.',
									search: (prev) => ({...prev, completed: checked === true, canceled: checked === true}),
								});
							}}
						/>
						{t('operate.processes.filters.finishedInstances')}
					</Label>
					<div className="ml-6 flex flex-col gap-2">
						<StateCheckbox
							id="filter-completed"
							label={t('operate.processes.filters.completed')}
							checked={completed}
							icon={<StateIcon state="COMPLETED" size={16} />}
							onChange={(checked) => void navigate({to: '.', search: (prev) => ({...prev, completed: checked})})}
						/>
						<StateCheckbox
							id="filter-canceled"
							label={t('operate.processes.filters.canceled')}
							checked={canceled}
							icon={<StateIcon state="TERMINATED" size={16} />}
							onChange={(checked) => void navigate({to: '.', search: (prev) => ({...prev, canceled: checked})})}
						/>
					</div>
				</div>
			</section>
		</div>
	);
};

export {ProcessFilters};
