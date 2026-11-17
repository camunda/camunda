/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Modal} from '@carbon/react';
import type {AgentInstanceMessageContent} from '@camunda/camunda-api-zod-schemas/8.11';
import {CopyButton} from '#/operate/shared/CopyButton/CopyButton';
import {RichTextEditor} from '#/operate/shared/Editors/RichTextEditor';
import {getToolCallResult} from './getToolCallResult';
import {Column, ColumnLabel, Columns, Description, EditorContainer, EmptyHint} from './toolResult.styled';

const PREVIEW_OPTIONS = {overviewRulerLanes: 0};

type ToolResultModalProps = {
	toolName: string;
	description: string | null;
	input: Record<string, unknown> | null;
	content: AgentInstanceMessageContent[];
	onClose: () => void;
};

const ToolResultModal: React.FC<ToolResultModalProps> = ({toolName, description, input, content, onClose}) => {
	const {t} = useTranslation();
	const inputValue = input === null ? null : JSON.stringify(input, null, 2);
	const result = getToolCallResult(content);
	const inputLabel = t('operate.processInstance.details.agent.input');
	const outputLabel = t('operate.processInstance.details.agent.output');

	return (
		<Modal
			open
			modalHeading={t('operate.processInstance.details.agent.toolCallTitle', {name: toolName})}
			onRequestClose={onClose}
			closeButtonLabel={t('operate.shared.editors.close')}
			size="lg"
			passiveModal
		>
			{description && <Description>{description}</Description>}
			<Columns>
				<Column aria-label={inputLabel}>
					<ColumnLabel>{inputLabel}</ColumnLabel>
					{inputValue !== null && <CopyButton value={inputValue} hasIconOnly tooltipAlignment="end" />}
					{inputValue === null ? (
						<EmptyHint>{t('operate.processInstance.details.agent.noToolInput')}</EmptyHint>
					) : (
						<EditorContainer>
							<RichTextEditor value={inputValue} readOnly language="json" height="100%" options={PREVIEW_OPTIONS} />
						</EditorContainer>
					)}
				</Column>
				<Column aria-label={outputLabel}>
					<ColumnLabel>{outputLabel}</ColumnLabel>
					{result !== null && <CopyButton value={result.value} hasIconOnly tooltipAlignment="end" />}
					{result === null ? (
						<EmptyHint>{t('operate.processInstance.details.agent.emptyToolResult')}</EmptyHint>
					) : (
						<EditorContainer>
							<RichTextEditor
								value={result.value}
								readOnly
								language={result.language}
								height="100%"
								options={PREVIEW_OPTIONS}
							/>
						</EditorContainer>
					)}
				</Column>
			</Columns>
		</Modal>
	);
};

export {ToolResultModal};
