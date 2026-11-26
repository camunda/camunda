/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import {Button, EmptyState, linkVariants, Text} from '@camunda/design-system';
import {CircleCheck, FunnelX, Search} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';
import {SortableTable} from '#/operate/shared/SortableTable/shadcn.components/SortableTable';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import type {ChildInstanceWithIncident, DecisionInstanceLookup, EnhancedIncident} from '../incidents.types';
import {useIncidentsFilter} from '../useIncidentsFilter';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {DataLoadErrorAlert} from '#/operate/shared/DataLoadErrorAlert/shadcn.components/DataLoadErrorAlert';
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
	onRetry: () => void;
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
			<Link
				to="/operate/decisions/$decisionInstanceId"
				params={{decisionInstanceId: decisionInstance.decisionInstanceKey}}
				search={{tenantId}}
				className={cn(linkVariants(), 'block min-w-0')}
				data-interactive
			>
				<TruncatedText text={label} />
			</Link>
		);
	}

	if (incident.processInstanceKey !== processInstanceKey) {
		const label = `${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`;
		return (
			<Link
				to="/operate-preview/processes/$processInstanceId"
				params={{processInstanceId: incident.processInstanceKey}}
				search={{elementId: incident.elementId, tenantId}}
				className={cn(linkVariants(), 'block min-w-0')}
				data-interactive
			>
				<TruncatedText text={label} />
			</Link>
		);
	}

	return <TruncatedText text={incident.elementName} />;
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
	onRetry,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
}) => {
	const {t} = useTranslation();
	const {errorTypes, setErrorTypes} = useIncidentsFilter();
	const {hasSelection} = useProcessInstanceElementSelection();

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
						<TruncatedText text={incident.errorMessage} />
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
					<EmptyState
						size="sm"
						headingLevel={3}
						icon={<Search aria-hidden />}
						heading={t('operate.processInstance.ds.incidents.noElementIncidentsHeading')}
						description={t('operate.processInstance.incidents.calledInstanceInfo')}
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
							to="/operate/decisions/$decisionInstanceId"
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

	if (state === 'error') {
		return <DataLoadErrorAlert onRetry={onRetry} />;
	}

	const emptyState =
		state !== 'empty' ? undefined : errorTypes.length > 0 ? (
			<EmptyState
				size="sm"
				headingLevel={3}
				icon={<FunnelX aria-hidden />}
				heading={t('operate.processInstance.ds.incidents.noMatchesHeading')}
				description={t('operate.processInstance.ds.incidents.noMatchesDescription')}
				action={
					<Button size="sm" onClick={() => setErrorTypes([])}>
						{t('operate.shared.ds.clearFilters')}
					</Button>
				}
			/>
		) : hasSelection ? (
			<EmptyState
				size="sm"
				headingLevel={3}
				icon={<CircleCheck aria-hidden />}
				heading={t('operate.processInstance.ds.incidents.noElementIncidentsHeading')}
				description={t('operate.processInstance.ds.incidents.noElementIncidentsDescription')}
			/>
		) : (
			<EmptyState
				size="sm"
				headingLevel={3}
				icon={<CircleCheck aria-hidden />}
				heading={t('operate.processInstance.ds.incidents.emptyHeading')}
				description={t('operate.processInstance.ds.incidents.emptyDescription')}
			/>
		);

	return (
		<SortableTable
			ariaLabel={t('operate.processInstance.incidents.list')}
			className="[&_tbody_tr:has([data-selected-row])]:bg-[var(--neutral-background-medium)] [&_td:has([data-error-message])]:w-full [&_td:has([data-error-message])]:max-w-0 [&_td:has([data-error-message])]:min-w-64 [&_td:has([data-element-name])]:max-w-48 [&_td]:whitespace-nowrap [&_th]:whitespace-nowrap"
			borderless
			columns={columns}
			rows={state === 'empty' ? [] : incidents}
			rowKey={(incident) => incident.incidentKey}
			emptyState={emptyState}
			hideHeaderWhenEmpty
			isFetching={state === 'skeleton'}
			isFetchingPreviousPage={isFetchingPreviousPage}
			isFetchingNextPage={isFetchingNextPage}
			hasPreviousPage={hasPreviousPage}
			hasNextPage={hasNextPage}
			onRowClick={onSelect}
			expandRowLabel={t('operate.processInstance.incidents.expandRow')}
			collapseRowLabel={t('operate.processInstance.incidents.collapseRow')}
			expansion={(incident) => (
				<dl className="m-0 flex flex-col gap-3">
					<ExpansionField label={t('operate.processInstance.incidents.jobId')}>
						<dd className="m-0 text-sm">{incident.jobKey ?? t('operate.processInstance.header.none')}</dd>
					</ExpansionField>
					<ExpansionField label={t('operate.processInstance.incidents.errorMessage')}>
						<dd className="m-0 flex items-start gap-2 text-sm">
							<span className="min-w-0 break-words whitespace-pre-wrap">{incident.errorMessage}</span>
						</dd>
					</ExpansionField>
				</dl>
			)}
			onVerticalScrollStartReach={onVerticalScrollStartReach}
			onVerticalScrollEndReach={onVerticalScrollEndReach}
		/>
	);
};

export {IncidentsTable};
