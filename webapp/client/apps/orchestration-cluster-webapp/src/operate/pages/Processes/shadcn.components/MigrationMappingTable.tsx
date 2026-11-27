/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useMemo, useState} from 'react';
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
	onMappingChange,
}) => {
	const {t} = useTranslation();
	const [isNotMappedFilterEnabled, setIsNotMappedFilterEnabled] = useState(false);
	const embeddedFormWarningId = useId();
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
								onCheckedChange={setIsNotMappedFilterEnabled}
							/>
						</div>
					)}
					<Table className="table-fixed" containerClassName="rounded-none">
						<TableHeader>
							<TableRow>
								<TableHead className="w-1/2">{t('operate.processes.migration.sourceElements')}</TableHead>
								<TableHead className="w-1/2 border-l">{t('operate.processes.migration.targetElements')}</TableHead>
							</TableRow>
						</TableHeader>
						<TableBody>
							{rows.map(({sourceElement, choices}) => {
								const name = getNameOrId(sourceElement.name, sourceElement.id);
								const targetElementId = mapping[sourceElement.id] ?? '';
								const isSelected = selectedSourceElementIds?.includes(sourceElement.id) ?? false;

								return (
									<TableRow
										key={sourceElement.id}
										data-state={isSelected ? 'selected' : undefined}
										className={cn('cursor-pointer', isSelected && 'bg-accent')}
										onClick={() => onSourceElementSelection(sourceElement.id)}
									>
										<TableCell>
											<div className="flex items-center gap-2">
												{embeddedFormMigrationSourceIds.has(sourceElement.id) && (
													<Tooltip>
														<TooltipTrigger asChild>
															<span
																tabIndex={0}
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
													className="rounded-sm text-left focus-visible:outline-2 focus-visible:outline-offset-2"
													onClick={(event) => {
														event.stopPropagation();
														onSourceElementSelection(sourceElement.id);
													}}
												>
													{name}
												</button>
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
														onClick={(event) => event.stopPropagation()}
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
