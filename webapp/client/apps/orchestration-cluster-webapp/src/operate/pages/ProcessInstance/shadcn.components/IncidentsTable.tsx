/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import {Button, linkVariants, Text, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {SortableTable} from '#/operate/shared/SortableTable/shadcn.components/SortableTable';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {RichTextEditorModal} from '#/operate/shared/Editors/shadcn.components/RichTextEditorModal';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import type {ChildInstanceWithIncident, DecisionInstanceLookup, EnhancedIncident} from '../incidents.types';
import {shouldOfferErrorMessageModal} from '../shouldOfferErrorMessageModal';
import {IncidentOperation} from './IncidentOperation';

type Props = {
	state: 'skeleton' | 'error' | 'empty' | 'content';
	processInstanceKey: string;
	tenantId?: string;
	incidents: EnhancedIncident[];
	decisionInstancesByElementKey: DecisionInstanceLookup;
	childInstanceWithIncident?: ChildInstanceWithIncident;
	isProcessInstanceSuspended: boolean;
	hasPreviousPage: boolean;
	hasNextPage: boolean;
	isFetchingPreviousPage: boolean;
	isFetchingNextPage: boolean;
	onSelect: (incident: EnhancedIncident) => void;
	onVerticalScrollStartReach: () => void;
	onVerticalScrollEndReach: () => void;
};

const CENTERED_STATE_CLASS = 'flex flex-1 items-center justify-center p-4';

const ExpansionField: React.FC<{label: string; children: React.ReactNode}> = ({label, children}) => (
	<div className="flex flex-col gap-1">
		<dt>
			<Text as="strong" variant="label-sm">
				{label}
			</Text>
		</dt>
		{children}
	</div>
);

const TruncatedLink: React.FC<{label: string; children: React.ReactElement}> = ({label, children}) => (
	<Tooltip>
		<TooltipTrigger asChild>{children}</TooltipTrigger>
		<TooltipContent side="bottom" className="max-w-md break-words whitespace-pre-wrap">
			{label}
		</TooltipContent>
	</Tooltip>
);

const ElementName: React.FC<{
	incident: EnhancedIncident;
	processInstanceKey: string;
	tenantId?: string;
	decisionInstancesByElementKey: DecisionInstanceLookup;
}> = ({incident, processInstanceKey, tenantId, decisionInstancesByElementKey}) => {
	const decisionInstance =
		incident.errorType === 'DECISION_EVALUATION_ERROR' || incident.errorType === 'CALLED_DECISION_ERROR'
			? decisionInstancesByElementKey[incident.elementInstanceKey]
			: undefined;

	if (decisionInstance !== undefined) {
		const label = `${decisionInstance.decisionDefinitionName} - ${decisionInstance.decisionInstanceKey}`;
		return (
			<TruncatedLink label={label}>
				<Link
					to="/operate-preview/decisions/$decisionInstanceId"
					params={{decisionInstanceId: decisionInstance.decisionInstanceKey}}
					search={{tenantId}}
					className={cn(linkVariants(), 'block truncate')}
					data-interactive
				>
					{label}
				</Link>
			</TruncatedLink>
		);
	}

	if (incident.processInstanceKey !== processInstanceKey) {
		const label = `${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`;
		return (
			<TruncatedLink label={label}>
				<Link
					to="/operate-preview/processes/$processInstanceId"
					params={{processInstanceId: incident.processInstanceKey}}
					search={{elementId: incident.elementId, tenantId}}
					className={cn(linkVariants(), 'block truncate')}
					data-interactive
				>
					{label}
				</Link>
			</TruncatedLink>
		);
	}

	return (
		<Tooltip>
			<TooltipTrigger asChild>
				<span tabIndex={0} className="block truncate">
					{incident.elementName}
				</span>
			</TooltipTrigger>
			<TooltipContent side="bottom" className="max-w-md break-words whitespace-pre-wrap">
				{incident.elementName}
			</TooltipContent>
		</Tooltip>
	);
};

const IncidentsTable: React.FC<Props> = ({
	state,
	processInstanceKey,
	tenantId,
	incidents,
	decisionInstancesByElementKey,
	childInstanceWithIncident,
	isProcessInstanceSuspended,
	hasPreviousPage,
	hasNextPage,
	isFetchingPreviousPage,
	isFetchingNextPage,
	onSelect,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
}) => {
	const {t} = useTranslation();
	const [modal, setModal] = useState<{title: string; content: string} | null>(null);

	const columns = useMemo(
		() => [
			{
				key: 'errorMessage',
				label: t('operate.processInstance.incidents.errorMessage'),
				render: (incident: EnhancedIncident) => (
					<span
						data-error-message=""
						data-selected-row={incident.isSelected ? '' : undefined}
						className="block min-w-0"
					>
						{incident.isSelected && (
							<span className="sr-only">{t('operate.processInstance.incidents.selectedRow')}</span>
						)}
						<Tooltip>
							<TooltipTrigger asChild>
								<span tabIndex={0} className="block truncate">
									{incident.errorMessage}
								</span>
							</TooltipTrigger>
							<TooltipContent side="bottom" className="max-w-md break-words whitespace-pre-wrap">
								{incident.errorMessage}
							</TooltipContent>
						</Tooltip>
					</span>
				),
			},
			{
				key: 'errorType',
				label: t('operate.processInstance.incidents.type'),
				sortKey: 'errorType',
				render: (incident: EnhancedIncident) => t(`operate.processInstance.incidents.errorTypes.${incident.errorType}`),
			},
			{
				key: 'elementName',
				label: t('operate.processInstance.incidents.failingElement'),
				render: (incident: EnhancedIncident) => (
					<span data-element-name="" className="block min-w-0">
						<ElementName
							incident={incident}
							processInstanceKey={processInstanceKey}
							tenantId={tenantId}
							decisionInstancesByElementKey={decisionInstancesByElementKey}
						/>
					</span>
				),
			},
			{
				key: 'creationTime',
				label: t('operate.processInstance.incidents.created'),
				sortKey: 'creationTime',
				isDefault: true,
				defaultOrder: 'DESC' as const,
				render: (incident: EnhancedIncident) => formatTimestamp(incident.creationTime),
			},
			{
				key: 'operations',
				label: t('operate.processInstance.incidents.operations'),
				render: (incident: EnhancedIncident) =>
					incident.processInstanceKey === processInstanceKey ? (
						<IncidentOperation
							incidentKey={incident.incidentKey}
							jobKey={incident.jobKey}
							disabled={isProcessInstanceSuspended}
						/>
					) : null,
			},
		],
		[t, processInstanceKey, tenantId, decisionInstancesByElementKey, isProcessInstanceSuspended],
	);

	if (state === 'empty' && childInstanceWithIncident !== undefined) {
		const {type, key, name} = childInstanceWithIncident;
		const label = t('operate.processInstance.incidents.viewIn', {name});
		return (
			<div className={CENTERED_STATE_CLASS}>
				<div className="flex flex-col gap-4">
					<EmptyMessage
						message={t('operate.processInstance.incidents.noIncidentsOnElement')}
						additionalInfo={t('operate.processInstance.incidents.calledInstanceInfo')}
					/>
					{type === 'process' ? (
						<Link
							to="/operate-preview/processes/$processInstanceId"
							params={{processInstanceId: key}}
							search={{tenantId}}
							title={label}
							className={linkVariants()}
						>
							{label}
						</Link>
					) : (
						<Link
							to="/operate-preview/decisions/$decisionInstanceId"
							params={{decisionInstanceId: key}}
							search={{tenantId}}
							title={label}
							className={linkVariants()}
						>
							{label}
						</Link>
					)}
				</div>
			</div>
		);
	}

	if (state === 'empty' || state === 'error') {
		return (
			<div className={CENTERED_STATE_CLASS}>
				{state === 'empty' ? (
					<EmptyMessage message={t('operate.processInstance.incidents.noMatchingIncidents')} />
				) : (
					<ErrorMessage />
				)}
			</div>
		);
	}

	return (
		<>
			<SortableTable
				ariaLabel={t('operate.processInstance.incidents.list')}
				className="[&_tbody_tr:has([data-selected-row])]:bg-[var(--neutral-background-medium)] [&_td:has([data-error-message])]:w-full [&_td:has([data-error-message])]:max-w-0 [&_td:has([data-error-message])]:min-w-64 [&_td:has([data-element-name])]:max-w-48 [&_td]:whitespace-nowrap [&_th]:whitespace-nowrap"
				borderless
				columns={columns}
				rows={incidents}
				rowKey={(incident) => incident.incidentKey}
				isFetching={state === 'skeleton'}
				isFetchingPreviousPage={isFetchingPreviousPage}
				isFetchingNextPage={isFetchingNextPage}
				hasPreviousPage={hasPreviousPage}
				hasNextPage={hasNextPage}
				loadingPreviousPageLabel={t('operate.shared.sortableTable.loadingPreviousPage')}
				loadingNextPageLabel={t('operate.shared.sortableTable.loadingNextPage')}
				onRowClick={onSelect}
				expandRowLabel={t('operate.processInstance.incidents.expandRow')}
				collapseRowLabel={t('operate.processInstance.incidents.collapseRow')}
				expansion={(incident) => (
					<dl className="m-0 flex flex-col gap-3">
						<ExpansionField label={t('operate.processInstance.incidents.jobId')}>
							<dd className="m-0 text-sm">{incident.jobKey ?? '—'}</dd>
						</ExpansionField>
						<ExpansionField label={t('operate.processInstance.incidents.errorMessage')}>
							<dd className="m-0 flex items-start gap-2 text-sm">
								<span className="min-w-0 break-words whitespace-pre-wrap">{incident.errorMessage}</span>
								{shouldOfferErrorMessageModal(incident.errorMessage) && (
									<Button
										type="button"
										size="sm"
										variant="ghost"
										onClick={() =>
											setModal({
												title: t('operate.processInstance.incidents.errorModalTitle', {name: incident.elementName}),
												content: incident.errorMessage,
											})
										}
									>
										{t('operate.processInstance.incidents.more')}
									</Button>
								)}
							</dd>
						</ExpansionField>
					</dl>
				)}
				onVerticalScrollStartReach={onVerticalScrollStartReach}
				onVerticalScrollEndReach={onVerticalScrollEndReach}
			/>
			{modal !== null && (
				<RichTextEditorModal
					isVisible
					title={modal.title}
					value={modal.content}
					onClose={() => setModal(null)}
					readOnly
				/>
			)}
		</>
	);
};

export {IncidentsTable};
