/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {mutedTitle} from '#/operate/shared/mutedTitle';
import {PanelTitle} from '#/operate/shared/PanelTitle/shadcn.components/PanelTitle';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {CopiableContent} from '#/operate/shared/PanelHeader/shadcn.components/CopiableContent';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';
import {ProcessOperations} from './ProcessOperations';

const HeaderColumn: React.FC<{title: string; className?: string; children: React.ReactNode}> = ({
	title,
	className,
	children,
}) => (
	<div className={cn('flex min-w-0 flex-col gap-0.5', className)}>
		<span className={mutedTitle('label-sm')}>{title}</span>
		{children}
	</div>
);

type Props = {
	processDefinitionSelection: ProcessDefinitionSelection;
};

const DiagramHeader: React.FC<Props> = ({processDefinitionSelection}) => {
	const {t} = useTranslation();

	if (processDefinitionSelection.kind === 'no-match') {
		return <PanelHeader title={t('operate.processes.diagramHeader.title')} className="bg-transparent pr-0" />;
	}

	const {definition} = processDefinitionSelection;
	const name = getProcessDefinitionName(definition);
	const versionTag =
		processDefinitionSelection.kind === 'single-version' ? processDefinitionSelection.definition.versionTag : undefined;

	return (
		<header className="flex min-h-12 min-w-0 items-center gap-8 border-b border-border px-4 py-1">
			<HeaderColumn title={t('operate.processes.diagramHeader.processName')} className="shrink">
				<PanelTitle
					titleAttribute={`${t('operate.processes.diagramHeader.processName')}: ${name}`}
					className="block truncate"
				>
					{name}
				</PanelTitle>
			</HeaderColumn>
			<HeaderColumn title={t('operate.processes.diagramHeader.processId')} className="shrink">
				<CopiableContent
					className="-ml-3 min-w-0 shrink [&>button]:h-5! [&>button]:min-w-0 [&_code]:min-w-0 [&_code]:truncate"
					copyButtonDescription={t('operate.processes.diagramHeader.copyButtonDescription')}
					title={t('operate.processes.diagramHeader.copyButtonTitle', {id: definition.processDefinitionId})}
					content={definition.processDefinitionId}
				/>
			</HeaderColumn>
			{versionTag && (
				<HeaderColumn title={t('operate.processes.diagramHeader.versionTag')} className="shrink">
					<Badge variant="neutral" title={`${t('operate.processes.diagramHeader.versionTag')}: ${versionTag}`}>
						{versionTag}
					</Badge>
				</HeaderColumn>
			)}
			{processDefinitionSelection.kind === 'single-version' && (
				<ProcessOperations
					key={processDefinitionSelection.definition.processDefinitionKey}
					definition={processDefinitionSelection.definition}
				/>
			)}
		</header>
	);
};

export {DiagramHeader};
