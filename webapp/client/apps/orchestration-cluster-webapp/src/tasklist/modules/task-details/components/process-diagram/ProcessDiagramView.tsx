/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge, Card, CardAction, CardHeader, CardTitle} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {BPMNDiagram} from './BPMNDiagram';

type Props = {
	xml: string;
	elementId: string;
	processName: string;
	processVersion: number;
};

const ProcessDiagramView: React.FC<Props> = ({xml, elementId, processName, processVersion}) => {
	const {t} = useTranslation();

	return (
		<div className="flex h-full w-full flex-col p-4">
			<Card className="min-h-0 w-full flex-1 gap-0 py-0">
				<CardHeader className="border-b border-border py-4">
					<CardTitle>{processName}</CardTitle>
					<CardAction>
						<Badge variant="neutral">{t('tasklist.processViewProcessVersion', {version: processVersion})}</Badge>
					</CardAction>
				</CardHeader>
				<BPMNDiagram xml={xml} highlightActivity={elementId} />
			</Card>
		</div>
	);
};

export {ProcessDiagramView};
