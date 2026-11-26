/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Alert,
	Badge,
	EmptyState,
	Label,
	Link,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	StatusIcon,
	Switch,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from '@camunda/design-system';
import {ArrowRight, CircleCheck, Workflow} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';
import {getNameOrId} from '../getElementName';
import {getTargetChoices, hasEmbeddedForm, isCamundaUserTask, type MigrationElements} from '../migrationMapping';

const NOT_MAPPED = 'not-mapped';
const TARGET_PREFIX = 'target:';

type Props = {
	isTargetSelected: boolean;
	isSummaryStep: boolean;
	hasSourceXml: boolean;
	sourceElements: MigrationElements;
	targetElements?: MigrationElements;
	mapping: Record<string, string>;
	autoMapping: Record<string, string>;
	selectedSourceElementIds?: string[];
	onSourceElementSelection: (elementId: string) => void;
	onSourceElementKeyboardSelection: (elementId: string) => void;
	onMappingChange: (sourceElementId: string, targetElementId: string) => void;
};

const MigrationMappingTable: React.FC<Props> = ({
	isTargetSelected,
	isSummaryStep,
	hasSourceXml,
	sourceElements,
	targetElements,
	mapping,
	autoMapping,
	selectedSourceElementIds,
	onSourceElementSelection,
	onSourceElementKeyboardSelection,
	onMappingChange,
}) => {
	const {t} = useTranslation();
	const [isNotMappedFilterEnabled, setIsNotMappedFilterEnabled] = useState(false);
	const [focusedSourceElementId, setFocusedSourceElementId] = useState<string>();
	const [selectedRowAnnouncement, setSelectedRowAnnouncement] = useState('');
	const embeddedFormWarningId = useId();
	const rowButtonsRef = useRef<Map<string, HTMLButtonElement>>(new Map());
	const rows = useMemo(
		() =>
			targetElements === undefined
				? []
				: [...sourceElements.elements, ...sourceElements.sequenceFlows]
						.filter(({id}) => !isNotMappedFilterEnabled || mapping[id] === undefined)
						.map((sourceElement) => ({sourceElement, choices: getTargetChoices(sourceElement, targetElements)})),
		[sourceElements, targetElements, mapping, isNotMappedFilterEnabled],
	);
	const embeddedFormMigrationSourceIds = useMemo(() => {
		if (!isTargetSelected || targetElements === undefined) {
			return new Set<string>();
		}
		const sourceElementsById = new Map(sourceElements.elements.map((element) => [element.id, element]));
		const targetElementsById = new Map(targetElements.elements.map((element) => [element.id, element]));

		return new Set(
			Object.entries(mapping)
				.filter(
					([sourceElementId, targetElementId]) =>
						hasEmbeddedForm(sourceElementsById.get(sourceElementId)) &&
						isCamundaUserTask(targetElementsById.get(targetElementId)),
				)
				.map(([sourceElementId]) => sourceElementId),
		);
	}, [isTargetSelected, sourceElements, targetElements, mapping]);
	const focusedRowId = useMemo(() => {
		if (rows.length === 0) {
			return undefined;
		}
		if (
			focusedSourceElementId !== undefined &&
			rows.some(({sourceElement}) => sourceElement.id === focusedSourceElementId)
		) {
			return focusedSourceElementId;
		}
		const selectedRowId = rows
			.map(({sourceElement}) => sourceElement.id)
			.find((sourceElementId) => selectedSourceElementIds?.includes(sourceElementId));
		return selectedRowId ?? rows[0]?.sourceElement.id;
	}, [rows, focusedSourceElementId, selectedSourceElementIds]);
	const focusRow = (sourceElementId: string) => {
		setFocusedSourceElementId(sourceElementId);
		rowButtonsRef.current.get(sourceElementId)?.focus();
	};
	const announceRowSelection = (sourceElementId: string) => {
		const rowIndex = rows.findIndex(({sourceElement}) => sourceElement.id === sourceElementId);
		const row = rowIndex === -1 ? undefined : rows[rowIndex];
		if (row === undefined) {
			return;
		}
		setSelectedRowAnnouncement(
			t('operate.processes.migration.mappingRowSelected', {
				name: getNameOrId(row.sourceElement.name, row.sourceElement.id),
				index: rowIndex + 1,
				total: rows.length,
			}),
		);
	};

	return (
		<div className="flex h-full flex-col overflow-auto">
			{!hasSourceXml || (sourceElements.elements.length === 0 && sourceElements.sequenceFlows.length === 0) ? (
				<div className="flex flex-1 items-center justify-center">
					<EmptyState
						size="sm"
						headingLevel={3}
						icon={<Workflow aria-hidden />}
						heading={t('operate.processes.ds.migration.noElementsHeading')}
						description={t('operate.processes.ds.migration.noElementsDescription')}
					/>
				</div>
			) : (
				<>
					{isTargetSelected && (
						<div className="flex items-center justify-end gap-2 px-4 py-2">
							<Label htmlFor="not-mapped-toggle" className="font-normal">
								{t('operate.processes.migration.notMappedOnly')}
							</Label>
							<Switch
								id="not-mapped-toggle"
								size="sm"
								checked={isNotMappedFilterEnabled}
								onCheckedChange={(value) => {
									setSelectedRowAnnouncement('');
									setIsNotMappedFilterEnabled(value);
								}}
							/>
						</div>
					)}
					<Table className="table-fixed">
						<TableHeader>
							<TableRow>
								<TableHead className="w-1/2">{t('operate.processes.migration.sourceElements')}</TableHead>
								<TableHead className="w-1/2 border-l">{t('operate.processes.migration.targetElements')}</TableHead>
							</TableRow>
						</TableHeader>
						<TableBody>
							{rows.map(({sourceElement, choices}, rowIndex) => {
								const name = getNameOrId(sourceElement.name, sourceElement.id);
								const targetElementId = mapping[sourceElement.id] ?? '';
								const isSelected = selectedSourceElementIds?.includes(sourceElement.id) ?? false;
								const isFocusedRow = focusedRowId === sourceElement.id;
								const hasEmbeddedFormWarning = embeddedFormMigrationSourceIds.has(sourceElement.id);
								const embeddedFormDescriptionId = `embedded-form-description-${sourceElement.id}`;
								const selectTargetElement = () => {
									setSelectedRowAnnouncement('');
									setFocusedSourceElementId(sourceElement.id);
									onSourceElementSelection(sourceElement.id);
								};
								const selectFocusedRowByIndex = (index: number) => {
									const nextRowId = rows[Math.max(0, Math.min(index, rows.length - 1))]?.sourceElement.id;
									if (nextRowId === undefined) {
										return;
									}
									if (nextRowId !== focusedRowId) {
										announceRowSelection(nextRowId);
										onSourceElementKeyboardSelection(nextRowId);
									}
									focusRow(nextRowId);
								};

								return (
									<TableRow
										key={sourceElement.id}
										data-state={isSelected ? 'selected' : undefined}
										className={cn('cursor-pointer', isSelected && 'bg-accent')}
										onClick={selectTargetElement}
									>
										<TableCell>
											<div className="flex items-center gap-2">
												{hasEmbeddedFormWarning && (
													<Tooltip>
														<TooltipTrigger asChild>
															<span
																tabIndex={-1}
																role="img"
																aria-label={t('operate.processes.migration.embeddedFormElement')}
															>
																<StatusIcon variant="warning" />
															</span>
														</TooltipTrigger>
														<TooltipContent>{t('operate.processes.migration.embeddedFormElement')}</TooltipContent>
													</Tooltip>
												)}
												<button
													type="button"
													aria-pressed={isSelected}
													aria-describedby={hasEmbeddedFormWarning ? embeddedFormDescriptionId : undefined}
													tabIndex={isFocusedRow ? 0 : -1}
													className="rounded-sm text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
													ref={(element) => {
														if (element === null) {
															rowButtonsRef.current.delete(sourceElement.id);
															return;
														}
														rowButtonsRef.current.set(sourceElement.id, element);
													}}
													onFocus={() => {
														setFocusedSourceElementId(sourceElement.id);
													}}
													onKeyDown={(event) => {
														switch (event.key) {
															case 'ArrowDown':
																event.preventDefault();
																selectFocusedRowByIndex(rowIndex + 1);
																return;
															case 'ArrowUp':
																event.preventDefault();
																selectFocusedRowByIndex(rowIndex - 1);
																return;
															case 'Home':
																event.preventDefault();
																selectFocusedRowByIndex(0);
																return;
															case 'End':
																event.preventDefault();
																selectFocusedRowByIndex(rows.length - 1);
																return;
															default:
																return;
														}
													}}
													onClick={(event) => {
														event.stopPropagation();
														selectTargetElement();
													}}
												>
													{name}
												</button>
												{hasEmbeddedFormWarning && (
													<span id={embeddedFormDescriptionId} className="sr-only">
														{t('operate.processes.migration.embeddedFormElement')}
													</span>
												)}
												{isTargetSelected && mapping[sourceElement.id] === undefined && (
													<Badge variant="info">{t('operate.processes.migration.notMapped')}</Badge>
												)}
												<ArrowRight aria-hidden className="ml-auto size-4" />
											</div>
										</TableCell>
										<TableCell className="border-l">
											<div className="flex items-center gap-2">
												<Select
													disabled={isSummaryStep || choices.length === 0}
													value={targetElementId === '' ? NOT_MAPPED : `${TARGET_PREFIX}${targetElementId}`}
													onValueChange={(value) =>
														onMappingChange(
															sourceElement.id,
															value === NOT_MAPPED ? '' : value.slice(TARGET_PREFIX.length),
														)
													}
												>
													<SelectTrigger
														size="sm"
														className="w-full"
														aria-label={t('operate.processes.migration.targetFor', {name})}
														tabIndex={isFocusedRow ? 0 : -1}
														onClick={(event) => event.stopPropagation()}
														onFocus={() => setFocusedSourceElementId(sourceElement.id)}
														onKeyDown={(event) => event.stopPropagation()}
													>
														<SelectValue />
													</SelectTrigger>
													<SelectContent>
														<SelectItem value={NOT_MAPPED}>{t('operate.processes.migration.notMapped')}</SelectItem>
														{choices.map(({id, name: choiceName}) => (
															<SelectItem key={id} value={`${TARGET_PREFIX}${id}`}>
																{getNameOrId(choiceName, id)}
															</SelectItem>
														))}
													</SelectContent>
												</Select>
												{autoMapping[sourceElement.id] !== undefined && sourceElement.id === targetElementId && (
													<span title={t('operate.processes.migration.autoMapped')}>
														<CircleCheck
															aria-label={t('operate.processes.migration.autoMapped')}
															role="img"
															className="size-4 text-success"
														/>
													</span>
												)}
											</div>
										</TableCell>
									</TableRow>
								);
							})}
						</TableBody>
					</Table>
					<span aria-live="polite" aria-atomic className="sr-only">
						{selectedRowAnnouncement}
					</span>
				</>
			)}
			{isTargetSelected && embeddedFormMigrationSourceIds.size > 0 && (
				<Alert
					variant="warning"
					className="m-4 w-auto"
					description={
						<>
							<span id={embeddedFormWarningId}>{t('operate.processes.migration.embeddedFormWarning')}</span>{' '}
							<Link
								className="text-inherit underline"
								aria-describedby={embeddedFormWarningId}
								href="https://docs.camunda.io/docs/components/concepts/process-instance-migration/#migrate-job-worker-user-tasks-to-camunda-user-tasks"
								target="_blank"
								rel="noopener noreferrer"
							>
								{t('operate.processes.migration.embeddedFormLink')}
							</Link>
						</>
					}
				/>
			)}
		</div>
	);
};

export {MigrationMappingTable};
