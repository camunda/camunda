/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, useLayoutEffect, useRef} from 'react';
import {createPortal} from 'react-dom';
import {Trans, useTranslation} from 'react-i18next';
import {useQueryClient} from '@tanstack/react-query';
import {useRouter} from '@tanstack/react-router';
import {
	Button,
	DataTable,
	Link,
	Modal,
	Table,
	TableBody,
	TableCell,
	TableContainer,
	TableExpandHeader,
	TableExpandRow,
	TableExpandedRow,
	TableHead,
	TableRow,
} from '@carbon/react';
import type {CallHierarchy} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {RichTextEditor} from '#/operate/shared/Editors/RichTextEditor';
import {DiffEditor} from '#/operate/shared/Editors/DiffEditor';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useInstanceStatistics} from './instanceDiagram.queries';
import {variableQueryKeys} from './variables.queries';
import {buildModificationInstructions} from './modifications/modificationInstructions';
import {generateId} from './modifications/modificationOperations';
import {
	getElementModifications,
	getModificationsByElement,
	getVariableModifications,
	hasOrphanedVariableModifications,
	willAllElementsBeCanceled,
} from './modifications/modificationSelectors';
import {
	getRunningInstancesByElement,
	hasActivationInCanceledScope,
	withoutMovesFromCanceledScopes,
} from './modifications/modificationTargets';
import {
	SummaryActionCell,
	SummaryActionableNotification,
	SummaryEmptyCell,
	SummaryEmptyMessage,
	SummaryNotification,
	SummaryNotificationText,
	SummaryTableContainer,
	SummaryTableHeader,
	SummaryTitle,
	TruncatedValue,
	TruncatedValueContainer,
} from './processInstanceModification.styled';

const OPERATION_LABEL_KEYS = {
	ADD_TOKEN: 'operate.processInstance.modification.add',
	CANCEL_TOKEN: 'operate.processInstance.modification.cancel',
	MOVE_TOKEN: 'operate.processInstance.modification.move',
	ADD_VARIABLE: 'operate.processInstance.modification.add',
	EDIT_VARIABLE: 'operate.processInstance.modification.edit',
} as const;

const HTTP_STATUS_FORBIDDEN = 403;

type Props = {
	onClose: () => void;
};

function beautifyJSON(value: string) {
	try {
		return JSON.stringify(JSON.parse(value), null, '\t');
	} catch {
		return value;
	}
}

function ChildInstanceError() {
	const {t} = useTranslation();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const router = useRouter();
	const {data: callHierarchy} = useCallHierarchy(processInstanceId, {enabled: true});
	const findInHierarchy = (processInstanceKey: string | null) =>
		processInstanceKey === null
			? undefined
			: callHierarchy?.find((entry) => entry.processInstanceKey === processInstanceKey);
	const parentProcess = findInHierarchy(processInstance.parentProcessInstanceKey);
	const rootProcess = findInHierarchy(processInstance.rootProcessInstanceKey);

	if (parentProcess === undefined || rootProcess === undefined) {
		return (
			<SummaryNotification kind="error" hideCloseButton lowContrast title="">
				<SummaryNotificationText>
					{t('operate.processInstance.modification.childInstanceErrorFallback')}
				</SummaryNotificationText>
			</SummaryNotification>
		);
	}

	const getLink = ({processInstanceKey}: CallHierarchy) =>
		router.buildLocation({
			to: '/operate/processes/$processInstanceId',
			params: {processInstanceId: processInstanceKey},
		}).href;
	const getLabel = ({processDefinitionName, processInstanceKey}: CallHierarchy) =>
		processDefinitionName === undefined ? processInstanceKey : `${processDefinitionName} - ${processInstanceKey}`;

	return (
		<SummaryActionableNotification kind="error" inline hideCloseButton lowContrast title="" actionButtonLabel="">
			<SummaryNotificationText>
				<Trans
					i18nKey="operate.processInstance.modification.childInstanceError"
					values={{parentProcess: getLabel(parentProcess), rootProcess: getLabel(rootProcess)}}
					components={{
						parentLink: <Link href={getLink(parentProcess)} target="_blank" />,
						rootLink: <Link href={getLink(rootProcess)} target="_blank" />,
					}}
				/>
			</SummaryNotificationText>
		</SummaryActionableNotification>
	);
}

function ModificationSummaryModal({onClose}: Props) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {clearSelection} = useProcessInstanceElementSelection();
	const {state, dispatch} = useProcessInstanceModification();
	const {data: diagram} = useDiagramXml(processInstance.processDefinitionKey);
	const statistics = useInstanceStatistics(processInstance, diagram !== undefined && diagram.xml !== '');
	const runningInstancesByElement = getRunningInstancesByElement(statistics.data, diagram?.businessObjects);
	const modificationsByElement = getModificationsByElement(state, {
		businessObjects: diagram?.businessObjects,
		totalRunningInstancesByElement: runningInstancesByElement,
		totalRunningInstancesVisibleByElement: runningInstancesByElement,
	});
	const elementModifications = getElementModifications(state);
	const variableModifications = getVariableModifications(state);
	const effectiveState = withoutMovesFromCanceledScopes(state, diagram?.businessObjects, modificationsByElement);
	const isProcessCanceled = willAllElementsBeCanceled(effectiveState, statistics.data, modificationsByElement);
	const hasParentProcess = Boolean(processInstance.parentProcessInstanceKey);
	const areModificationsInvalid = isProcessCanceled && hasParentProcess;
	const hasOrphanedVariables = hasOrphanedVariableModifications(state, processInstanceId);
	const hasCanceledScopeConflict = hasActivationInCanceledScope(
		state,
		diagram?.businessObjects,
		modificationsByElement,
	);
	const hasCanceledMoveSource = effectiveState !== state;

	const elementTableRef = useRef<HTMLDivElement>(null);
	const variableTableRef = useRef<HTMLDivElement>(null);

	useLayoutEffect(() => {
		const elementTable = elementTableRef.current;
		const variableTable = variableTableRef.current;
		if (elementTable === null || variableTable === null) {
			return;
		}
		const variableScrollbarWidth = variableTable.offsetWidth - variableTable.scrollWidth;
		const elementScrollbarWidth = elementTable.offsetWidth - elementTable.scrollWidth;
		const isAligned = variableScrollbarWidth === elementScrollbarWidth;
		elementTable.style.paddingRight = isAligned ? '0px' : `${variableScrollbarWidth}px`;
		variableTable.style.paddingRight = isAligned ? '0px' : `${elementScrollbarWidth}px`;
	});

	const showError = (statusCode?: number) =>
		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.processInstance.modification.failed'),
			subtitle: t(
				statusCode === HTTP_STATUS_FORBIDDEN
					? 'operate.processInstance.modification.failedNoPermission'
					: 'operate.processInstance.modification.failedGeneric',
			),
			isDismissable: true,
		});

	const applyModifications = async () => {
		onClose();
		clearSelection();
		const requestId = generateId();
		const result = buildModificationInstructions(state, processInstanceId);
		dispatch({type: 'startApplyingModifications', requestId});

		if (result.status === 'missing-root-variable-host') {
			showError();
			dispatch({type: 'finishApplyingModifications', requestId});
			return;
		}

		const {error} = await request(endpoints.modifyProcessInstance(processInstanceId, result.instructions));

		if (error === null) {
			void queryClient.invalidateQueries({queryKey: ['elementInstanceWaitStates']});
			void queryClient.invalidateQueries({queryKey: ['instanceHistory', processInstanceId]});
			void queryClient.invalidateQueries({queryKey: variableQueryKeys.instanceVariables(processInstanceId)});
			void queryClient.invalidateQueries({queryKey: variableQueryKeys.variableValues()});
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('operate.processInstance.modification.applied'),
				isDismissable: true,
			});
		} else {
			showError(error.response?.status);
		}

		dispatch({type: 'finishApplyingModifications', requestId});
	};

	return createPortal(
		<Modal
			open
			modalHeading={t('operate.processInstance.modification.summaryTitle')}
			size="lg"
			primaryButtonText={t('operate.processInstance.modification.apply')}
			primaryButtonDisabled={
				areModificationsInvalid ||
				hasOrphanedVariables ||
				hasCanceledScopeConflict ||
				hasCanceledMoveSource ||
				state.modifications.length === 0
			}
			secondaryButtonText={t('operate.processes.toolbar.cancel')}
			onRequestClose={onClose}
			preventCloseOnClickOutside
			onRequestSubmit={() => void applyModifications()}
		>
			<p>
				{t('operate.processInstance.modification.summaryDescription', {
					processName: getProcessDefinitionName(processInstance),
					processInstanceKey: processInstanceId,
				})}
			</p>

			{isProcessCanceled && !hasParentProcess && (
				<SummaryNotification kind="warning" hideCloseButton lowContrast title="">
					<SummaryNotificationText>
						{t('operate.processInstance.modification.cancelEntireInstanceWarning')}
					</SummaryNotificationText>
				</SummaryNotification>
			)}
			{areModificationsInvalid && <ChildInstanceError />}
			{hasOrphanedVariables && (
				<SummaryNotification
					kind="error"
					hideCloseButton
					lowContrast
					title={t('operate.processInstance.modification.invalidPlanTitle')}
				>
					<SummaryNotificationText>
						{t('operate.processInstance.modification.orphanedVariablesError')}
					</SummaryNotificationText>
				</SummaryNotification>
			)}
			{hasCanceledScopeConflict && (
				<SummaryNotification
					kind="error"
					hideCloseButton
					lowContrast
					title={t('operate.processInstance.modification.invalidPlanTitle')}
				>
					<SummaryNotificationText>
						{t('operate.processInstance.modification.canceledScopeConflictError')}
					</SummaryNotificationText>
				</SummaryNotification>
			)}
			{hasCanceledMoveSource && (
				<SummaryNotification
					kind="error"
					hideCloseButton
					lowContrast
					title={t('operate.processInstance.modification.invalidPlanTitle')}
				>
					<SummaryNotificationText>
						{t('operate.processInstance.modification.canceledMoveSourceError')}
					</SummaryNotificationText>
				</SummaryNotification>
			)}

			<SummaryTitle>{t('operate.processInstance.modification.elementModifications')}</SummaryTitle>
			{elementModifications.length === 0 ? (
				<SummaryEmptyMessage>{t('operate.processInstance.modification.noElementModifications')}</SummaryEmptyMessage>
			) : (
				<SummaryTableContainer ref={elementTableRef}>
					<Table size="sm">
						<TableHead>
							<TableRow>
								<SummaryTableHeader />
								<SummaryTableHeader $width="25%">
									{t('operate.processInstance.modification.operation')}
								</SummaryTableHeader>
								<SummaryTableHeader $width="25%">
									{t('operate.processInstance.modification.element')}
								</SummaryTableHeader>
								<SummaryTableHeader $width="25%">
									{t('operate.processInstance.modification.instanceKey')}
								</SummaryTableHeader>
								<SummaryTableHeader $width="20%">
									{t('operate.processInstance.modification.affectedTokens')}
								</SummaryTableHeader>
								<SummaryTableHeader $width="5%" />
							</TableRow>
						</TableHead>
						<TableBody>
							{elementModifications.map((modification, index) => {
								const elementId =
									modification.operation === 'MOVE_TOKEN' ? modification.targetElement.id : modification.element.id;

								return (
									<TableRow key={index}>
										<TableCell>
											<SummaryEmptyCell />
										</TableCell>
										<TableCell>{t(OPERATION_LABEL_KEYS[modification.operation])}</TableCell>
										<TableCell>
											<TruncatedValueContainer>
												{modification.operation === 'MOVE_TOKEN' ? (
													<>
														<TruncatedValue $hasMultipleTruncatedValue>{modification.element.name}</TruncatedValue>
														&nbsp;→&nbsp;
														<TruncatedValue $hasMultipleTruncatedValue>
															{modification.targetElement.name}
														</TruncatedValue>
													</>
												) : (
													<TruncatedValue>{modification.element.name}</TruncatedValue>
												)}
											</TruncatedValueContainer>
										</TableCell>
										<TableCell>
											<TruncatedValueContainer>
												{modification.operation !== 'ADD_TOKEN' && modification.elementInstanceKey !== undefined ? (
													<TruncatedValue>{modification.elementInstanceKey}</TruncatedValue>
												) : (
													'--'
												)}
											</TruncatedValueContainer>
										</TableCell>
										<TableCell data-testid="affected-token-count">
											{modification.operation === 'CANCEL_TOKEN'
												? modification.visibleAffectedTokenCount +
													(modificationsByElement[elementId]?.cancelledChildTokens ?? 0)
												: modification.affectedTokenCount}
										</TableCell>
										<SummaryActionCell>
											<Button
												kind="danger--ghost"
												title={t('operate.processInstance.modification.deleteElementModification')}
												aria-label={t('operate.processInstance.modification.deleteElementModification')}
												size="sm"
												onClick={() => dispatch({type: 'removeElementModification', modification})}
											>
												{t('operate.processInstance.modification.delete')}
											</Button>
										</SummaryActionCell>
									</TableRow>
								);
							})}
						</TableBody>
					</Table>
				</SummaryTableContainer>
			)}

			<SummaryTitle>{t('operate.processInstance.modification.variableModifications')}</SummaryTitle>
			{variableModifications.length === 0 ? (
				<SummaryEmptyMessage>{t('operate.processInstance.modification.noVariableModifications')}</SummaryEmptyMessage>
			) : (
				<SummaryTableContainer ref={variableTableRef}>
					<DataTable
						size="sm"
						headers={[
							{key: 'operation', header: t('operate.processInstance.modification.operation')},
							{key: 'scope', header: t('operate.processInstance.modification.scope')},
							{key: 'nameValue', header: t('operate.processInstance.modification.nameValue')},
							{key: 'emptyCell', header: ''},
							{key: 'delete', header: ''},
						]}
						rows={variableModifications.map(({scopeId, id}) => ({id: `${scopeId}/${id}`}))}
					>
						{({rows, getTableProps, getRowProps, getExpandHeaderProps}) => (
							<TableContainer>
								<Table {...getTableProps()}>
									<TableHead>
										<TableRow>
											<TableExpandHeader {...getExpandHeaderProps()} />
											<SummaryTableHeader $width="25%">
												{t('operate.processInstance.modification.operation')}
											</SummaryTableHeader>
											<SummaryTableHeader $width="25%">
												{t('operate.processInstance.modification.scope')}
											</SummaryTableHeader>
											<SummaryTableHeader $width="25%">
												{t('operate.processInstance.modification.nameValue')}
											</SummaryTableHeader>
											<SummaryTableHeader $width="20%" />
											<SummaryTableHeader $width="5%" />
										</TableRow>
									</TableHead>
									<TableBody>
										{rows.map((row, index) => {
											const modification = variableModifications[index];
											if (modification === undefined) {
												return null;
											}
											const {key, ...rowProps} = getRowProps({row});
											const {operation, elementName, name, newValue, oldValue, scopeId, id} = modification;

											return (
												<Fragment key={key}>
													<TableExpandRow
														{...rowProps}
														title={t('operate.processInstance.modification.variableChangesTitle')}
														id={`expanded-row-${row.id}`}
													>
														<TableCell>{t(OPERATION_LABEL_KEYS[operation])}</TableCell>
														<TableCell>
															<TruncatedValue>{elementName}</TruncatedValue>
														</TableCell>
														<TableCell>
															<TruncatedValueContainer>
																<TruncatedValue>{`${name}: ${newValue}`}</TruncatedValue>
															</TruncatedValueContainer>
														</TableCell>
														<TableCell />
														<SummaryActionCell>
															<Button
																kind="danger--ghost"
																title={t('operate.processInstance.modification.deleteVariableModification')}
																aria-label={t('operate.processInstance.modification.deleteVariableModification')}
																size="sm"
																onClick={() =>
																	dispatch({
																		type: 'removeVariableModification',
																		scopeId,
																		id,
																		operation,
																		source: 'summaryModal',
																	})
																}
															>
																{t('operate.processInstance.modification.delete')}
															</Button>
														</SummaryActionCell>
													</TableExpandRow>
													<TableExpandedRow colSpan={6}>
														{row.isExpanded &&
															(operation === 'ADD_VARIABLE' ? (
																<RichTextEditor
																	value={beautifyJSON(newValue)}
																	readOnly
																	autoFocus={false}
																	height="10vh"
																	width="95%"
																/>
															) : (
																<DiffEditor
																	modifiedValue={beautifyJSON(newValue)}
																	originalValue={beautifyJSON(oldValue ?? '')}
																	height="10vh"
																	width="95%"
																/>
															))}
													</TableExpandedRow>
												</Fragment>
											);
										})}
									</TableBody>
								</Table>
							</TableContainer>
						)}
					</DataTable>
				</SummaryTableContainer>
			)}
		</Modal>,
		document.getElementById('main-content') ?? document.body,
	);
}

export {ModificationSummaryModal};
