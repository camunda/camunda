/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {PanelHeader, CopiableContent} from './styled';
import type {DecisionDefinitionSelection} from './DecisionPanel';
import {getDecisionDefinitionName} from './getDecisionDefinitionName';

type Props = {
	decisionDefinitionSelection: DecisionDefinitionSelection;
	children?: React.ReactNode;
};

const DecisionHeader: React.FC<Props> = ({decisionDefinitionSelection, children}) => {
	const {t} = useTranslation();
	const title =
		decisionDefinitionSelection.kind === 'no-match'
			? t('operate.decisions.diagramHeader.title')
			: getDecisionDefinitionName(decisionDefinitionSelection.definition);

	return (
		<PanelHeader title={title}>
			{decisionDefinitionSelection.kind !== 'no-match' && (
				<CopiableContent
					copyButtonDescription={t('operate.decisions.diagramHeader.copyButtonDescription')}
					content={decisionDefinitionSelection.definition.decisionDefinitionId}
				/>
			)}
			{children}
		</PanelHeader>
	);
};

export {DecisionHeader};
