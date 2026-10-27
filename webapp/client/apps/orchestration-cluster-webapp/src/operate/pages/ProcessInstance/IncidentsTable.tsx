/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {createLink} from '@tanstack/react-router';
import {
	Button,
	DataTable,
	Link,
	Stack,
	Table,
	TableBody,
	TableCell,
	TableExpandHeader,
	TableHead,
	TableHeader,
	TableRow,
	Tooltip,
} from '@carbon/react';
import type {Incident} from '@camunda/camunda-api-zod-schemas/8.11';
import {ColumnHeader} from '#/operate/shared/SortableTable/ColumnHeader';
import {InfiniteScroller} from '#/operate/shared/InfiniteScroller/InfiniteScroller';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {RichTextEditorModal} from '#/operate/shared/Editors/RichTextEditorModal';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {IncidentOperation} from './IncidentOperation';
import {
	ChildIncidentContainer,
	DataTableSkeleton,
	EmptyMessageContainer,
	ErrorMessageCell,
	ExpandedContent,
	ExpandedField,
	FieldLabel,
	FlexContainer,
	ScrollContainer,
	TableContainer,
	TableExpandRow,
	TableExpandedRow,
	TruncatedText,
} from './incidents.styled';

const InstanceLink = createLink(Link);

// The expanded row wraps the full message, so the modal is reserved for stack traces and unusually
// large single-line messages where its dedicated viewer adds value over the inline content.
const MODAL_WORTHY_ERROR_MESSAGE_LENGTH = 200;
const SKELETON_ROW_COUNT = 10;

type EnhancedIncident = Incident & {
	processDefinitionName: string;
	elementName: string;
	isSelected: boolean;
};

type DecisionInstanceLookup = Record<string, {decisionInstanceKey: string; decisionDefinitionName: string}>;

type ChildInstanceWithIncident = {
	type: 'process' | 'decision';
	key: string;
	name: string;
};

type Props = {
	state: 'skeleton' | 'error' | 'empty' | 'content';
	processInstanceKey: string;
	tenantId?: string;
	incidents: EnhancedIncident[];
	decisionInstancesByElementKey: DecisionInstanceLookup;
	childInstanceWithIncident?: ChildInstanceWithIncident;
	isProcessInstanceSuspended: boolean;
	onSelect: (incident: EnhancedIncident) => void;
	onVerticalScrollStartReach: React.ComponentProps<typeof InfiniteScroller>['onVerticalScrollStartReach'];
	onVerticalScrollEndReach: React.ComponentProps<typeof InfiniteScroller>['onVerticalScrollEndReach'];
};

function shouldOfferErrorMessageModal(errorMessage: string): boolean {
	return errorMessage.includes('\n') || errorMessage.length >= MODAL_WORTHY_ERROR_MESSAGE_LENGTH;
}

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

	const stopPropagation = (event: React.MouseEvent) => event.stopPropagation();
	const isOtherInstance = incident.processInstanceKey !== processInstanceKey;
	const label =
		decisionInstance !== undefined
			? `${decisionInstance.decisionDefinitionName} - ${decisionInstance.decisionInstanceKey}`
			: isOtherInstance
				? `${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`
				: incident.elementName;

	return (
		<Tooltip align="bottom-start" autoAlign description={label}>
			<TruncatedText tabIndex={decisionInstance === undefined && !isOtherInstance ? 0 : undefined}>
				{decisionInstance !== undefined ? (
					<InstanceLink
						to="/operate/decisions/$decisionInstanceId"
						params={{decisionInstanceId: decisionInstance.decisionInstanceKey}}
						search={{tenantId}}
						title={t('operate.processInstance.incidents.viewRootCauseDecision', {label})}
						onClick={stopPropagation}
					>
						{label}
					</InstanceLink>
				) : isOtherInstance ? (
					<InstanceLink
						to="/operate/processes/$processInstanceId"
						params={{processInstanceId: incident.processInstanceKey}}
						search={{elementId: incident.elementId, tenantId}}
						title={t('operate.processInstance.incidents.viewRootCauseInstance', {
							name: incident.processDefinitionName,
							key: incident.processInstanceKey,
						})}
						onClick={stopPropagation}
					>
						{label}
					</InstanceLink>
				) : (
					label
				)}
			</TruncatedText>
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
	onSelect,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
}) => {
	const {t} = useTranslation();
	const scrollableContainerRef = useRef<HTMLDivElement | null>(null);
	const [modal, setModal] = useState<{title: string; content: string} | null>(null);
	const headers = [
		{key: 'errorMessage', header: t('operate.processInstance.incidents.errorMessage')},
		{key: 'errorType', header: t('operate.processInstance.incidents.type'), sortKey: 'errorType'},
		{key: 'elementName', header: t('operate.processInstance.incidents.failingElement')},
		{
			key: 'creationTime',
			header: t('operate.processInstance.incidents.created'),
			sortKey: 'creationTime',
			isDefault: true,
		},
		{key: 'operations', header: t('operate.processInstance.incidents.operations')},
	];

	if (state === 'empty' && childInstanceWithIncident !== undefined) {
		const {type, key, name} = childInstanceWithIncident;
		const label = t('operate.processInstance.incidents.viewIn', {name});
		return (
			<ChildIncidentContainer>
				<Stack gap={5}>
					<EmptyMessage
						message={t('operate.processInstance.incidents.noIncidentsOnElement')}
						additionalInfo={t('operate.processInstance.incidents.calledInstanceInfo')}
					/>
					{type === 'process' ? (
						<InstanceLink
							to="/operate/processes/$processInstanceId"
							params={{processInstanceId: key}}
							search={{tenantId}}
							title={label}
						>
							{label}
						</InstanceLink>
					) : (
						<InstanceLink
							to="/operate/decisions/$decisionInstanceId"
							params={{decisionInstanceId: key}}
							search={{tenantId}}
							title={label}
						>
							{label}
						</InstanceLink>
					)}
				</Stack>
			</ChildIncidentContainer>
		);
	}

	if (state === 'empty' || state === 'error') {
		return (
			<EmptyMessageContainer>
				{state === 'empty' ? (
					<EmptyMessage message={t('operate.processInstance.incidents.noMatchingIncidents')} />
				) : (
					<ErrorMessage />
				)}
			</EmptyMessageContainer>
		);
	}

	if (state === 'skeleton') {
		return (
			<DataTableSkeleton
				data-testid="incidents-skeleton"
				columnCount={headers.length}
				rowCount={SKELETON_ROW_COUNT}
				showHeader={false}
				showToolbar={false}
				headers={headers.map(({header}) => ({header}))}
			/>
		);
	}

	const incidentsByKey = new Map(incidents.map((incident) => [incident.incidentKey, incident]));
	const rows = incidents.map((incident) => ({
		id: incident.incidentKey,
		errorMessage: (
			<Tooltip align="bottom-start" autoAlign description={incident.errorMessage}>
				<TruncatedText tabIndex={0}>{incident.errorMessage}</TruncatedText>
			</Tooltip>
		),
		errorType: t(`operate.processInstance.incidents.errorTypes.${incident.errorType}`),
		elementName: (
			<ElementName
				incident={incident}
				processInstanceKey={processInstanceKey}
				tenantId={tenantId}
				decisionInstancesByElementKey={decisionInstancesByElementKey}
			/>
		),
		creationTime: formatTimestamp(incident.creationTime),
		operations:
			incident.processInstanceKey === processInstanceKey ? (
				<IncidentOperation
					incidentKey={incident.incidentKey}
					jobKey={incident.jobKey}
					disabled={isProcessInstanceSuspended}
				/>
			) : undefined,
	}));

	return (
		<>
			<ScrollContainer ref={scrollableContainerRef}>
				<DataTable rows={rows} headers={headers} size="sm">
					{({rows, getTableProps, getRowProps, getExpandHeaderProps}) => (
						<TableContainer>
							<Table {...getTableProps()} isSortable>
								<TableHead>
									<TableRow>
										<TableExpandHeader
											{...getExpandHeaderProps()}
											aria-label={t('operate.processInstance.incidents.expandRow')}
										/>
										{headers.map(({key, header, sortKey, isDefault}) =>
											sortKey === undefined ? (
												<TableHeader key={key} isSortable={false}>
													{header}
												</TableHeader>
											) : (
												<ColumnHeader key={key} sortKey={sortKey} label={header} isDefault={isDefault} />
											),
										)}
									</TableRow>
								</TableHead>
								<InfiniteScroller
									onVerticalScrollStartReach={onVerticalScrollStartReach}
									onVerticalScrollEndReach={onVerticalScrollEndReach}
									scrollableContainerRef={scrollableContainerRef}
								>
									<TableBody aria-live="polite">
										{rows.map((row) => {
											const incident = incidentsByKey.get(row.id);
											if (incident === undefined) {
												return null;
											}
											const {key, onExpand, ...rowProps} = getRowProps({row});
											return (
												<Fragment key={key}>
													<TableExpandRow
														{...rowProps}
														onExpand={(event) => {
															event.stopPropagation();
															onExpand(event);
														}}
														isSelected={incident.isSelected}
														aria-selected={incident.isSelected}
														onClick={() => onSelect(incident)}
													>
														{row.cells.map((cell) => (
															<TableCell key={cell.id} data-column-key={cell.info.header}>
																{cell.value}
															</TableCell>
														))}
													</TableExpandRow>
													<TableExpandedRow colSpan={headers.length + 1}>
														{rowProps.isExpanded ? (
															<ExpandedContent>
																<ExpandedField>
																	<FieldLabel>{t('operate.processInstance.incidents.jobId')}</FieldLabel>
																	<span>{incident.jobKey ?? '—'}</span>
																</ExpandedField>
																<ExpandedField>
																	<FieldLabel>{t('operate.processInstance.incidents.errorMessage')}</FieldLabel>
																	<FlexContainer>
																		<ErrorMessageCell>{incident.errorMessage}</ErrorMessageCell>
																		{shouldOfferErrorMessageModal(incident.errorMessage) && (
																			<Button
																				size="sm"
																				kind="ghost"
																				onClick={(event) => {
																					event.stopPropagation();
																					setModal({
																						title: t('operate.processInstance.incidents.errorModalTitle', {
																							name: incident.elementName,
																						}),
																						content: incident.errorMessage,
																					});
																				}}
																			>
																				{t('operate.processInstance.incidents.more')}
																			</Button>
																		)}
																	</FlexContainer>
																</ExpandedField>
															</ExpandedContent>
														) : null}
													</TableExpandedRow>
												</Fragment>
											);
										})}
									</TableBody>
								</InfiniteScroller>
							</Table>
						</TableContainer>
					)}
				</DataTable>
			</ScrollContainer>
			<RichTextEditorModal
				isVisible={modal !== null}
				title={modal?.title}
				value={modal?.content ?? ''}
				onClose={() => setModal(null)}
				readOnly
			/>
		</>
	);
};

export {IncidentsTable};
export type {DecisionInstanceLookup, ChildInstanceWithIncident};
