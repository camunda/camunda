/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, useState} from 'react';
import {Trans, useTranslation} from 'react-i18next';
import {useRouter} from '@tanstack/react-router';
import {
	Alert,
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Heading,
	Link,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from '@camunda/design-system';
import {ChevronDown, ChevronRight} from '@camunda/design-system/icons';
import type {CallHierarchy} from '@camunda/camunda-api-zod-schemas/8.11';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';
import {DiffEditor} from '#/operate/shared/Editors/shadcn.components/DiffEditor';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {
	beautifyJSON,
	getCallHierarchyLabel,
	OPERATION_LABEL_KEYS,
	useChildInstanceErrorProcesses,
	useModificationSummary,
} from '../useModificationSummary';

type Props = {
	onClose: () => void;
};

const ChildInstanceError: React.FC = () => {
	const {t} = useTranslation();
	const router = useRouter();
	const {parentProcess, rootProcess} = useChildInstanceErrorProcesses();

	if (parentProcess === undefined || rootProcess === undefined) {
		return (
			<Alert
				variant="destructive"
				role="alert"
				description={t('operate.processInstance.modification.childInstanceErrorFallback')}
			/>
		);
	}

	const getLink = ({processInstanceKey}: CallHierarchy) =>
		router.buildLocation({
			to: '/operate-preview/processes/$processInstanceId',
			params: {processInstanceId: processInstanceKey},
		}).href;

	return (
		<Alert
			variant="destructive"
			role="alert"
			description={
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
			}
		/>
	);
};

const ModificationSummaryModal: React.FC<Props> = ({onClose}) => {
	const {t} = useTranslation();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
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
		applyModifications,
		removeElementModification,
		removeVariableModification,
	} = useModificationSummary(onClose);

	const toggleExpanded = (key: string) =>
		setExpandedKeys((current) => {
			const next = new Set(current);
			if (!next.delete(key)) {
				next.add(key);
			}
			return next;
		});

	const invalidPlanErrors = [
		{
			isVisible: hasOrphanedVariables,
			key: 'orphanedVariables',
			text: t('operate.processInstance.modification.orphanedVariablesError'),
		},
		{
			isVisible: hasCanceledScopeConflict,
			key: 'canceledScopeConflict',
			text: t('operate.processInstance.modification.canceledScopeConflictError'),
		},
		{
			isVisible: hasCanceledMoveSource,
			key: 'canceledMoveSource',
			text: t('operate.processInstance.modification.canceledMoveSourceError'),
		},
	].filter(({isVisible}) => isVisible);

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="lg" dismissOnOutsideClick={false}>
				<DialogHeader>
					<DialogTitle>{t('operate.processInstance.modification.summaryTitle')}</DialogTitle>
					<DialogDescription>
						{t('operate.processInstance.modification.summaryDescription', {
							processName: getProcessDefinitionName(processInstance),
							processInstanceKey: processInstanceId,
						})}
					</DialogDescription>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4">
					{isProcessCanceled && !hasParentProcess && (
						<Alert
							variant="warning"
							role="alert"
							description={t('operate.processInstance.modification.cancelEntireInstanceWarning')}
						/>
					)}
					{areModificationsInvalid && <ChildInstanceError />}
					{invalidPlanErrors.map(({key, text}) => (
						<Alert
							key={key}
							variant="destructive"
							role="alert"
							title={t('operate.processInstance.modification.invalidPlanTitle')}
							description={text}
						/>
					))}

					<section className="flex flex-col gap-2">
						<Heading as="h3" variant="label-md-strong">
							{t('operate.processInstance.modification.elementModifications')}
						</Heading>
						{elementModifications.length === 0 ? (
							<p className="text-sm text-muted-foreground">
								{t('operate.processInstance.modification.noElementModifications')}
							</p>
						) : (
							<Table size="sm">
								<TableHeader>
									<TableRow>
										<TableHead>{t('operate.processInstance.modification.operation')}</TableHead>
										<TableHead>{t('operate.processInstance.modification.element')}</TableHead>
										<TableHead>{t('operate.processInstance.modification.instanceKey')}</TableHead>
										<TableHead>{t('operate.processInstance.modification.affectedTokens')}</TableHead>
										<TableHead>
											<span className="sr-only">{t('operate.processInstance.modification.delete')}</span>
										</TableHead>
									</TableRow>
								</TableHeader>
								<TableBody>
									{elementModifications.map((modification, index) => {
										const elementId =
											modification.operation === 'MOVE_TOKEN' ? modification.targetElement.id : modification.element.id;

										return (
											<TableRow key={index}>
												<TableCell>{t(OPERATION_LABEL_KEYS[modification.operation])}</TableCell>
												<TableCell className="max-w-48 truncate">
													{modification.operation === 'MOVE_TOKEN'
														? `${modification.element.name} → ${modification.targetElement.name}`
														: modification.element.name}
												</TableCell>
												<TableCell className="max-w-48 truncate">
													{modification.operation !== 'ADD_TOKEN' && modification.elementInstanceKey !== undefined
														? modification.elementInstanceKey
														: '--'}
												</TableCell>
												<TableCell>
													{modification.operation === 'CANCEL_TOKEN'
														? modification.visibleAffectedTokenCount +
															(modificationsByElement[elementId]?.cancelledChildTokens ?? 0)
														: modification.affectedTokenCount}
												</TableCell>
												<TableCell>
													<Button
														variant="ghost"
														size="sm"
														className="text-destructive"
														title={t('operate.processInstance.modification.deleteElementModification')}
														aria-label={t('operate.processInstance.modification.deleteElementModification')}
														onClick={() => removeElementModification(modification)}
													>
														{t('operate.processInstance.modification.delete')}
													</Button>
												</TableCell>
											</TableRow>
										);
									})}
								</TableBody>
							</Table>
						)}
					</section>

					<section className="flex flex-col gap-2">
						<Heading as="h3" variant="label-md-strong">
							{t('operate.processInstance.modification.variableModifications')}
						</Heading>
						{variableModifications.length === 0 ? (
							<p className="text-sm text-muted-foreground">
								{t('operate.processInstance.modification.noVariableModifications')}
							</p>
						) : (
							<Table size="sm">
								<TableHeader>
									<TableRow>
										<TableHead>
											<span className="sr-only">{t('operate.processInstance.modification.variableChangesTitle')}</span>
										</TableHead>
										<TableHead>{t('operate.processInstance.modification.operation')}</TableHead>
										<TableHead>{t('operate.processInstance.modification.scope')}</TableHead>
										<TableHead>{t('operate.processInstance.modification.nameValue')}</TableHead>
										<TableHead>
											<span className="sr-only">{t('operate.processInstance.modification.delete')}</span>
										</TableHead>
									</TableRow>
								</TableHeader>
								<TableBody>
									{variableModifications.map((modification) => {
										const {scopeId, id, operation, elementName, name, newValue, oldValue} = modification;
										const key = `${scopeId}/${id}`;
										const isExpanded = expandedKeys.has(key);
										const ToggleIcon = isExpanded ? ChevronDown : ChevronRight;

										return (
											<Fragment key={key}>
												<TableRow>
													<TableCell>
														<Button
															variant="ghost"
															size="sm"
															aria-expanded={isExpanded}
															title={t('operate.processInstance.modification.variableChangesTitle')}
															aria-label={t('operate.processInstance.modification.variableChangesTitle')}
															onClick={() => toggleExpanded(key)}
														>
															<ToggleIcon aria-hidden="true" />
														</Button>
													</TableCell>
													<TableCell>{t(OPERATION_LABEL_KEYS[operation])}</TableCell>
													<TableCell className="max-w-48 truncate">{elementName}</TableCell>
													<TableCell className="max-w-64 truncate">{`${name}: ${newValue}`}</TableCell>
													<TableCell>
														<Button
															variant="ghost"
															size="sm"
															className="text-destructive"
															title={t('operate.processInstance.modification.deleteVariableModification')}
															aria-label={t('operate.processInstance.modification.deleteVariableModification')}
															onClick={() => removeVariableModification(modification)}
														>
															{t('operate.processInstance.modification.delete')}
														</Button>
													</TableCell>
												</TableRow>
												{isExpanded && (
													<TableRow>
														<TableCell colSpan={5}>
															{operation === 'ADD_VARIABLE' ? (
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
															)}
														</TableCell>
													</TableRow>
												)}
											</Fragment>
										);
									})}
								</TableBody>
							</Table>
						)}
					</section>
				</DialogBody>
				<DialogFooter>
					<Button variant="secondary" onClick={onClose}>
						{t('operate.processes.toolbar.cancel')}
					</Button>
					<Button disabled={isApplyDisabled} onClick={() => void applyModifications()}>
						{t('operate.processInstance.modification.apply')}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {ModificationSummaryModal};
