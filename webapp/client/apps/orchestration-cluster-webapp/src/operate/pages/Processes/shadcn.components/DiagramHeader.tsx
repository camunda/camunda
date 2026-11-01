/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {CopiableContent} from '#/operate/shared/PanelHeader/shadcn.components/CopiableContent';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';
import {ProcessOperations} from './ProcessOperations';

const SEPARATOR_CLASS_NAME =
	'relative ml-12 before:absolute before:-left-4 before:h-6 before:w-px before:bg-[var(--neutral-border-subtle)]';

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
		<PanelHeader
			title={name}
			titleAttribute={`${t('operate.processes.diagramHeader.processName')}: ${name}`}
			className="min-w-0 bg-transparent pr-0 [&>h2]:block [&>h2]:min-w-0 [&>h2]:truncate"
		>
			<CopiableContent
				className={`${SEPARATOR_CLASS_NAME} min-w-0 shrink [&_code]:min-w-0 [&_code]:truncate [&>button]:min-w-0`}
				copyButtonDescription={t('operate.processes.ds.diagramHeader.copyButtonDescription')}
				title={t('operate.processes.ds.diagramHeader.copyButtonTitle', {id: definition.processDefinitionId})}
				content={definition.processDefinitionId}
			/>
			{versionTag && (
				<span className={`${SEPARATOR_CLASS_NAME} min-w-0 shrink overflow-hidden`}>
					<Badge variant="neutral" title={`${t('operate.processes.diagramHeader.versionTag')}: ${versionTag}`}>
						{versionTag}
					</Badge>
				</span>
			)}
			{processDefinitionSelection.kind === 'single-version' && (
				<ProcessOperations
					key={processDefinitionSelection.definition.processDefinitionKey}
					definition={processDefinitionSelection.definition}
				/>
			)}
		</PanelHeader>
	);
};

export {DiagramHeader};
