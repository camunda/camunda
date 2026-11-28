/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {Text} from '@camunda/design-system';
import type {AgentTool} from '@camunda/camunda-api-zod-schemas/8.11';

type AvailableToolsProps = {
	tools: AgentTool[];
};

const AvailableTools: React.FC<AvailableToolsProps> = ({tools}) => {
	const {t} = useTranslation();
	const id = useId();

	if (tools.length === 0) {
		return (
			<Text as="span" variant="body-sm" className="text-[var(--neutral-foreground-subtle)]">
				{t('operate.processInstance.details.agent.noTools')}
			</Text>
		);
	}

	const sortedTools = [...tools].sort((a, b) => a.name.localeCompare(b.name));

	return (
		<ul className="m-0 flex list-none flex-col gap-4 p-0">
			{sortedTools.map((tool, index) => (
				<li key={tool.name} aria-labelledby={`${id}-${index}`}>
					<Text as="span" variant="heading-xs" id={`${id}-${index}`} className="text-foreground">
						{tool.name}
					</Text>
					{tool.description !== null && (
						<Text as="p" variant="body-sm" className="m-0 text-[var(--neutral-foreground-subtle)]">
							{tool.description}
						</Text>
					)}
				</li>
			))}
		</ul>
	);
};

export {AvailableTools};
