/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment} from 'react';
import {createPortal} from 'react-dom';
import {Trans, useTranslation} from 'react-i18next';
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
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {RichTextEditor} from '#/operate/shared/Editors/RichTextEditor';
import {DiffEditor} from '#/operate/shared/Editors/DiffEditor';
import {useProcessInstancePage} from './useProcessInstancePage';
import {
	beautifyJSON,
	getCallHierarchyLabel,
	OPERATION_LABEL_KEYS,
	useChildInstanceErrorProcesses,
	useModificationSummary,
} from './useModificationSummary';
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

type Props = {
	onClose: () => void;
};

function ChildInstanceError() {
	const {t} = useTranslation();
	const router = useRouter();
	const {parentProcess, rootProcess} = useChildInstanceErrorProcesses();

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
	return (
		<SummaryActionableNotification kind="error" inline hideCloseButton lowContrast title="" actionButtonLabel="">
			<SummaryNotificationText>
				<Trans
					i18nKey="operate.processInstance.modification.childInstanceError"
					values={{
						parentProcess: getCallHierarchyLabel(parentProcess),
						rootProcess: getCallHierarchyLabel(rootProcess),
					}}
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
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {
		elementModifications,
		variableModifications,
		modificationsByElement,
		isProcessCanceled,
		hasParentProcess,
		areModificationsInvalid,
		hasOrphanedVariables,
		hasCanceledScopeConflict,
		hasCanceledMoveSource,
		isApplyDisabled,
		elementTableRef,
		variableTableRef,
		applyModifications,
		removeElementModification,
		removeVariableModification,
	} = useModificationSummary(onClose);

	return createPortal(
		<Modal
			open
			modalHeading={t('operate.processInstance.modification.summaryTitle')}
			size="lg"
			primaryButtonText={t('operate.processInstance.modification.apply')}
			primaryButtonDisabled={isApplyDisabled}
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
												onClick={() => removeElementModification(modification)}
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
											const {operation, elementName, name, newValue, oldValue} = modification;

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
																onClick={() => removeVariableModification(modification)}
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
