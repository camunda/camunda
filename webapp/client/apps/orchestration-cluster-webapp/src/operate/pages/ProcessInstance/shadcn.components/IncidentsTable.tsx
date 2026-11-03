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
import {Button, linkVariants, Text} from '@camunda/design-system';
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

const TruncatedText: React.FC<{title: string; isFocusable?: boolean; children: React.ReactNode}> = ({
	title,
	isFocusable = false,
	children,
}) => (
	<div
		title={title}
		tabIndex={isFocusable ? 0 : undefined}
		className="min-w-0 overflow-hidden text-sm text-ellipsis whitespace-nowrap"
	>
		{children}
	</div>
);

const ElementName: React.FC<{
	incident: EnhancedIncident;
	processInstanceKey: string;
	tenantId?: string;
	decisionInstancesByElementKey: DecisionInstanceLookup;
}> = ({incident, processInstanceKey, tenantId, decisionInstancesByElementKey}) => {
	const {t} = useTranslation();
	const decisionInstance =
		incident.errorType === 'DECISION_EVALUATION_ERROR' || incident.errorType === 'CALLED_DECISION_ERROR'
			? decisionInstancesByElementKey[incident.elementInstanceKey]
			: undefined;
	const isOtherInstance = incident.processInstanceKey !== processInstanceKey;
	const label =
		decisionInstance !== undefined
			? `${decisionInstance.decisionDefinitionName} - ${decisionInstance.decisionInstanceKey}`
			: isOtherInstance
				? `${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`
				: incident.elementName;

	return (
		<TruncatedText title={label} isFocusable={decisionInstance === undefined && !isOtherInstance}>
			{decisionInstance !== undefined ? (
				<Link
					to="/operate-preview/decisions/$decisionInstanceId"
					params={{decisionInstanceId: decisionInstance.decisionInstanceKey}}
					search={{tenantId}}
					title={t('operate.processInstance.incidents.viewRootCauseDecision', {label})}
					className={linkVariants()}
					data-interactive
				>
					{label}
				</Link>
			) : isOtherInstance ? (
				<Link
					to="/operate-preview/processes/$processInstanceId"
					params={{processInstanceId: incident.processInstanceKey}}
					search={{elementId: incident.elementId, tenantId}}
					title={t('operate.processInstance.incidents.viewRootCauseInstance', {
						name: incident.processDefinitionName,
						key: incident.processInstanceKey,
					})}
					className={linkVariants()}
					data-interactive
				>
					{label}
				</Link>
			) : (
				label
			)}
		</TruncatedText>
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
					<div className="flex min-w-0 items-center gap-2" data-selected-row={incident.isSelected ? '' : undefined}>
						{incident.isSelected && (
							<span className="sr-only">{t('operate.processInstance.incidents.selectedRow')}</span>
						)}
						<TruncatedText title={incident.errorMessage} isFocusable>
							{incident.errorMessage}
						</TruncatedText>
					</div>
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
					<ElementName
						incident={incident}
						processInstanceKey={processInstanceKey}
						tenantId={tenantId}
						decisionInstancesByElementKey={decisionInstancesByElementKey}
					/>
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
			<div className="flex flex-1 items-center justify-center p-4">
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
			<div className="flex flex-1 items-center justify-center p-4">
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
				className="[&_tbody_tr:has([data-selected-row])]:bg-neutral-background-medium"
				ariaLabel={t('operate.processInstance.incidents.list')}
				size="sm"
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
						<div className="flex flex-col gap-1">
							<dt>
								<Text as="strong" variant="label-sm">
									{t('operate.processInstance.incidents.jobId')}
								</Text>
							</dt>
							<dd className="m-0 text-sm">{incident.jobKey ?? '—'}</dd>
						</div>
						<div className="flex flex-col gap-1">
							<dt>
								<Text as="strong" variant="label-sm">
									{t('operate.processInstance.incidents.errorMessage')}
								</Text>
							</dt>
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
						</div>
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
