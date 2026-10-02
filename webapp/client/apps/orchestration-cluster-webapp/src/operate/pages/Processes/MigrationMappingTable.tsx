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
	Callout,
	Link,
	SelectItem,
	Stack,
	Table,
	TableBody,
	TableCell,
	TableContainer,
	TableHead,
	TableRow,
	Tag,
	Tile,
	Toggle,
	Tooltip,
} from '@carbon/react';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {getTargetChoices, hasEmbeddedForm, isCamundaUserTask, type MigrationElements} from './migrationMapping';
import {
	MigrationArrowRight,
	MigrationCheckmarkFilled,
	MigrationIconContainer,
	MigrationMapping,
	MigrationMessageContainer,
	MigrationSourceElement,
	MigrationSourceElementName,
	MigrationTableHeader,
	MigrationTableRow,
	MigrationTargetSelect,
	MigrationToggleContainer,
	MigrationWarningFilled,
} from './styled';

type Props = {
	isTargetSelected: boolean;
	hasSourceXml: boolean;
	sourceElements: MigrationElements;
	targetElements?: MigrationElements;
	mapping: Record<string, string>;
	autoMapping: Record<string, string>;
	selectedSourceElementIds?: string[];
	onSourceElementSelection: (elementId: string) => void;
	onMappingChange: (sourceElementId: string, targetElementId: string) => void;
};

function MigrationMappingTable({
	isTargetSelected,
	hasSourceXml,
	sourceElements,
	targetElements,
	mapping,
	autoMapping,
	selectedSourceElementIds,
	onSourceElementSelection,
	onMappingChange,
}: Props) {
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
	const hasEmbeddedFormMigration = embeddedFormMigrationSourceIds.size > 0;

	return (
		<MigrationMapping>
			{!hasSourceXml || (sourceElements.elements.length === 0 && sourceElements.sequenceFlows.length === 0) ? (
				<MigrationMessageContainer>
					<ErrorMessage
						message={t('operate.processes.migration.noElements')}
						additionalInfo={t('operate.processes.migration.noElementsInfo')}
					/>
				</MigrationMessageContainer>
			) : (
				<>
					{isTargetSelected && (
						<MigrationToggleContainer>
							<Toggle
								size="sm"
								id="not-mapped-toggle"
								labelA={t('operate.processes.migration.notMappedOnly')}
								labelB={t('operate.processes.migration.notMappedOnly')}
								aria-label={t('operate.processes.migration.notMappedOnly')}
								toggled={isNotMappedFilterEnabled}
								onToggle={setIsNotMappedFilterEnabled}
							/>
							<MigrationArrowRight />
						</MigrationToggleContainer>
					)}
					<TableContainer>
						<Table size="md">
							<TableHead>
								<TableRow>
									<MigrationTableHeader>{t('operate.processes.migration.sourceElements')}</MigrationTableHeader>
									<MigrationTableHeader>{t('operate.processes.migration.targetElements')}</MigrationTableHeader>
								</TableRow>
							</TableHead>
							<TableBody>
								{rows.map(({sourceElement, choices}) => {
									const name = sourceElement.name ?? sourceElement.id;
									const targetElementId = mapping[sourceElement.id] ?? '';
									const isSelected = selectedSourceElementIds?.includes(sourceElement.id) ?? false;

									return (
										<MigrationTableRow
											key={sourceElement.id}
											isSelected={isSelected}
											aria-selected={isSelected}
											tabIndex={0}
											onClick={() => onSourceElementSelection(sourceElement.id)}
											onKeyDown={({key}) => {
												if (key === 'Enter') {
													onSourceElementSelection(sourceElement.id);
												}
											}}
										>
											<TableCell>
												<MigrationSourceElement>
													{embeddedFormMigrationSourceIds.has(sourceElement.id) && (
														<Tooltip
															label={t('operate.processes.migration.embeddedFormElement')}
															align="right-bottom"
															leaveDelayMs={50}
														>
															<MigrationWarningFilled />
														</Tooltip>
													)}
													<MigrationSourceElementName>{name}</MigrationSourceElementName>
													{isTargetSelected && mapping[sourceElement.id] === undefined && (
														<Tag type="blue">{t('operate.processes.migration.notMapped')}</Tag>
													)}
													<MigrationArrowRight />
												</MigrationSourceElement>
											</TableCell>
											<TableCell>
												<Stack orientation="horizontal" gap={4}>
													<MigrationTargetSelect
														disabled={choices.length === 0}
														size="sm"
														hideLabel
														labelText={t('operate.processes.migration.targetFor', {name})}
														id={sourceElement.id}
														value={targetElementId}
														onChange={({target}) => onMappingChange(sourceElement.id, target.value)}
													>
														{[{id: '', name: ''}, ...choices].map(({id, name: choiceName}) => (
															<SelectItem key={id} value={id} text={choiceName ?? id} />
														))}
													</MigrationTargetSelect>
													{autoMapping[sourceElement.id] !== undefined && sourceElement.id === targetElementId && (
														<MigrationIconContainer title={t('operate.processes.migration.autoMapped')}>
															<MigrationCheckmarkFilled />
														</MigrationIconContainer>
													)}
												</Stack>
											</TableCell>
										</MigrationTableRow>
									);
								})}
							</TableBody>
						</Table>
					</TableContainer>
				</>
			)}
			{isTargetSelected && hasEmbeddedFormMigration && (
				<Tile>
					<Callout
						kind="warning"
						subtitle={<span id={embeddedFormWarningId}>{t('operate.processes.migration.embeddedFormWarning')}</span>}
						lowContrast
					>
						<Link
							aria-describedby={embeddedFormWarningId}
							href="https://docs.camunda.io/docs/components/concepts/process-instance-migration/#migrate-job-worker-user-tasks-to-camunda-user-tasks"
							target="_blank"
						>
							{t('operate.processes.migration.embeddedFormLink')}
						</Link>
					</Callout>
				</Tile>
			)}
		</MigrationMapping>
	);
}

export {MigrationMappingTable};
