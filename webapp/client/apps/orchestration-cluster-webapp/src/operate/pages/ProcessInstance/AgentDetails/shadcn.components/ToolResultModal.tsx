/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Dialog, DialogBody, DialogContent, DialogHeader, DialogTitle, Heading, Text} from '@camunda/design-system';
import type {AgentInstanceMessageContent} from '@camunda/camunda-api-zod-schemas/8.11';
import {CopyButton} from '#/operate/shared/CopyButton/shadcn.components/CopyButton';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';
import {getToolCallResult} from '../getToolCallResult';

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
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="lg" aria-describedby={undefined} closeLabel={t('operate.shared.editors.close')}>
				<DialogHeader>
					<DialogTitle>{t('operate.processInstance.details.agent.toolCallTitle', {name: toolName})}</DialogTitle>
				</DialogHeader>
				<DialogBody className="space-y-4">
					{description && (
						<Text as="p" variant="body-sm" className="m-0 text-[var(--neutral-foreground-subtle)]">
							{description}
						</Text>
					)}
					<div className="flex flex-wrap gap-4">
						<section
							aria-label={inputLabel}
							className="grid min-w-0 flex-[1_1_22rem] grid-cols-[1fr_auto] items-center gap-3"
						>
							<Heading as="h3" variant="heading-xs" className="m-0 text-foreground">
								{inputLabel}
							</Heading>
							{inputValue !== null && <CopyButton value={inputValue} hasIconOnly tooltipAlignment="end" />}
							{inputValue === null ? (
								<span className="col-span-full self-start text-sm text-[var(--neutral-foreground-subtle)]">
									{t('operate.processInstance.details.agent.noToolInput')}
								</span>
							) : (
								<div className="col-span-full h-[40vh] self-start">
									<RichTextEditor value={inputValue} readOnly language="json" height="100%" options={PREVIEW_OPTIONS} />
								</div>
							)}
						</section>
						<section
							aria-label={outputLabel}
							className="grid min-w-0 flex-[1_1_22rem] grid-cols-[1fr_auto] items-center gap-3"
						>
							<Heading as="h3" variant="heading-xs" className="m-0 text-foreground">
								{outputLabel}
							</Heading>
							{result !== null && <CopyButton value={result.value} hasIconOnly tooltipAlignment="end" />}
							{result === null ? (
								<span className="col-span-full self-start text-sm text-[var(--neutral-foreground-subtle)]">
									{t('operate.processInstance.details.agent.emptyToolResult')}
								</span>
							) : (
								<div className="col-span-full h-[40vh] self-start">
									<RichTextEditor
										value={result.value}
										readOnly
										language={result.language}
										height="100%"
										options={PREVIEW_OPTIONS}
									/>
								</div>
							)}
						</section>
					</div>
				</DialogBody>
			</DialogContent>
		</Dialog>
	);
};

export {ToolResultModal};
