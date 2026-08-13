/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Tag} from '#/shared/design-system-compat';
import {Card} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
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

	return (
		<Layer className={styles.container}>
			<div className={styles.header}>
				<span className={cn(styles.processName, featureFlags.dsTasklistUI && styles.processNameDS)}>
					{processName}
				</span>
				<Tag className={styles.version}>{t('tasklist.processViewProcessVersion', {version: processVersion})}</Tag>
			</div>
			{/* DS-only: the diagram canvas sat flush on the panel background. The DS Card
			    gives it the same raised surface as the embedded form and the task cards.
			    No CardContent wrapper — the canvas fills the card edge to edge, and Card
			    already clips to its own rounded corners. Keep in sync with
			    TaskDetailsProcessSkeleton, which stands in for this while the diagram
			    loads. */}
			{featureFlags.dsTasklistUI ? (
				<Card className={styles.diagramCard}>
					<BPMNDiagram xml={xml} highlightActivity={elementId} />
				</Card>
			) : (
				<Layer className={styles.diagramFrame}>
					<BPMNDiagram xml={xml} highlightActivity={elementId} />
				</Layer>
			)}
		</Layer>
	);
};

export {ProcessDiagramView};
