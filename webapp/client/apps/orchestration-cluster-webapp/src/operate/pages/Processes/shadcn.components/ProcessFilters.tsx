/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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
		processItems,
		selectedProcess,
		versionNumbers,
		isDefinitionsReady,
		isDefinitionsError,
		isDefinitionsFetching,
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

	const renderLoadError = (error: unknown, onRetry: () => void) => (
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

	const stateCheckbox = (
		id: string,
		label: string,
		checked: boolean | 'indeterminate',
		icon: React.ReactNode,
		onChange: (checked: boolean) => void,
	) => (
		<Label htmlFor={id} className="cursor-pointer font-normal">
			<Checkbox id={id} checked={checked} onCheckedChange={(next) => onChange(next === true)} />
			{icon}
			{label}
		</Label>
	);

	const processOptions = (
		selectedProcess !== null && !processItems.some(({id}) => id === selectedProcess.id)
			? [...processItems, selectedProcess]
			: processItems
	).map(({id, label}) => ({value: id, label}));

	const elementOptions = (
		selectedElement !== null && !elementItems.some(({id}) => id === selectedElement.id)
			? [...elementItems, selectedElement]
			: elementItems
	).map(({id, label}) => ({value: id, label}));

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
							onValueChange={(value) => {
								if (value === selectedProcess?.id) {
									return;
								}
								const item = processItems.find(({id}) => id === value);
								void navigate({
									to: '.',
									search: (prev) => ({
										...prev,
										...(isMultiTenancyEnabled && item?.tenantId !== undefined ? {tenantId: item.tenantId} : {}),
										process: item?.processDefinitionId,
										version: item?.version,
										elementId: undefined,
									}),
								});
							}}
						/>
					</div>
					<div className="flex flex-col gap-1.5">
						<Label htmlFor="process-version-filter">{t('operate.processes.filters.version')}</Label>
						<Select
							value={selectedVersion}
							disabled={!isDefinitionsReady || versionNumbers.length === 0}
							onValueChange={(value) => {
								if (value === '') {
									return;
								}
								void navigate({
									to: '.',
									search: (prev) => ({
										...prev,
										version: value === ALL_VERSIONS ? undefined : Number(value),
										elementId: undefined,
									}),
								});
							}}
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
					{isDefinitionsError &&
						!isDefinitionsFetching &&
						renderLoadError(selectedDefinitionsError, () => void refetchSelectedDefinitions())}
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
							onValueChange={(value) => {
								void navigate({to: '.', search: (prev) => ({...prev, elementId: value})});
							}}
						/>
					</div>
					{isXmlError &&
						selectedDefinitionKey !== undefined &&
						renderLoadError(xmlError, () => void refetchDiagramXml())}
					{isElementDisabled && elementId && (
						<Button
							variant="ghost"
							size="sm"
							onClick={() => {
								void navigate({to: '.', search: (prev) => ({...prev, elementId: undefined})});
							}}
						>
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
						{stateCheckbox(
							'filter-active',
							t('operate.processes.filters.active'),
							active,
							<StateIcon state="ACTIVE" size={16} />,
							(checked) => void navigate({to: '.', search: (prev) => ({...prev, active: checked})}),
						)}
						{stateCheckbox(
							'filter-incidents',
							t('operate.processes.filters.incidents'),
							incidents,
							<StateIcon state="INCIDENT" size={16} />,
							(checked) => void navigate({to: '.', search: (prev) => ({...prev, incidents: checked})}),
						)}
						{stateCheckbox(
							'filter-suspended',
							t('operate.processes.filters.suspended'),
							suspended,
							<StateIcon state="SUSPENDED" size={16} />,
							(checked) => void navigate({to: '.', search: (prev) => ({...prev, suspended: checked})}),
						)}
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
						{stateCheckbox(
							'filter-completed',
							t('operate.processes.filters.completed'),
							completed,
							<StateIcon state="COMPLETED" size={16} />,
							(checked) => void navigate({to: '.', search: (prev) => ({...prev, completed: checked})}),
						)}
						{stateCheckbox(
							'filter-canceled',
							t('operate.processes.filters.canceled'),
							canceled,
							<StateIcon state="TERMINATED" size={16} />,
							(checked) => void navigate({to: '.', search: (prev) => ({...prev, canceled: checked})}),
						)}
					</div>
				</div>
			</section>
		</div>
	);
};

export {ProcessFilters};
