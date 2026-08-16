/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Tag} from '#/shared/design-system-compat';
import {Card, CardAction, CardHeader, CardTitle} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {featureFlags} from '#/shared/feature-flags';
import {BPMNDiagram} from './BPMNDiagram';
import styles from './ProcessDiagramView.module.scss';

type Props = {
	xml: string;
	elementId: string;
	processName: string;
	processVersion: number;
};

const ProcessDiagramView: React.FC<Props> = ({xml, elementId, processName, processVersion}) => {
	const {t} = useTranslation();
	const versionTag = (
		<Tag className={styles.version}>{t('tasklist.processViewProcessVersion', {version: processVersion})}</Tag>
	);

	// DS-only: the process name/version moved inside the diagram Card as its
	// own CardHeader (title left, version CardAction right), instead of a
	// separate row sitting above the card — per explicit request, so the
	// Task/Process/History tabs' main tile (form Card, diagram Card, history
	// table) all start at the same top edge when switching tabs. Carbon keeps
	// the original standalone header above its own Layer frame, unchanged.
	// Keep the header in sync with TaskDetailsProcessSkeleton, which mirrors
	// this structure for the loading state.
	if (featureFlags.dsTasklistUI) {
		return (
			<Layer className={styles.container}>
				<Card className={styles.diagramCard}>
					<CardHeader className={styles.diagramCardHeaderDS}>
						<CardTitle>{processName}</CardTitle>
						<CardAction>{versionTag}</CardAction>
					</CardHeader>
					<BPMNDiagram xml={xml} highlightActivity={elementId} />
				</Card>
			</Layer>
		);
	}

	return (
		<Layer className={styles.container}>
			<div className={styles.header}>
				<span className={styles.processName}>{processName}</span>
				{versionTag}
			</div>
			<Layer className={styles.diagramFrame}>
				<BPMNDiagram xml={xml} highlightActivity={elementId} />
			</Layer>
		</Layer>
	);
};

export {ProcessDiagramView};
