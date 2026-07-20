/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {CopyButton} from '#/operate/shared/CopyButton/CopyButton';
import {PanelHeader, Description, DescriptionTitle, DescriptionData} from './styled';
import type {ProcessDefinitionSelection} from './DiagramPanel';

function getProcessDefinitionName(definition: {name: string | null; processDefinitionId: string}) {
	return definition.name ?? definition.processDefinitionId;
}

type Props = {
	processDefinitionSelection: ProcessDefinitionSelection;
	panelHeaderRef?: React.RefObject<HTMLDivElement | null>;
};

const DiagramHeader: React.FC<Props> = ({processDefinitionSelection, panelHeaderRef}) => {
	const {t} = useTranslation();
	const title = processDefinitionSelection.kind === 'no-match' ? t('operate.processes.diagramHeader.title') : undefined;

	if (processDefinitionSelection.kind === 'no-match') {
		return <PanelHeader title={title} ref={panelHeaderRef} />;
	}

	const {definition} = processDefinitionSelection;
	const name = getProcessDefinitionName(definition);
	const versionTag =
		processDefinitionSelection.kind === 'single-version' ? processDefinitionSelection.definition.versionTag : undefined;

	return (
		<PanelHeader ref={panelHeaderRef}>
			<Description>
				<DescriptionTitle>{t('operate.processes.diagramHeader.processName')}</DescriptionTitle>
				<DescriptionData title={name} role="heading" aria-level={2}>
					{name}
				</DescriptionData>
			</Description>
			<Description>
				<DescriptionTitle>{t('operate.processes.diagramHeader.processId')}</DescriptionTitle>
				<DescriptionData title={definition.processDefinitionId}>
					{definition.processDefinitionId}
					<CopyButton value={definition.processDefinitionId} hasIconOnly />
				</DescriptionData>
			</Description>
			{versionTag && (
				<Description>
					<DescriptionTitle>{t('operate.processes.diagramHeader.versionTag')}</DescriptionTitle>
					<DescriptionData title={versionTag}>{versionTag}</DescriptionData>
				</Description>
			)}
		</PanelHeader>
	);
};

export {DiagramHeader};
