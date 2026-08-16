/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText} from '#/shared/design-system-compat';
import {Card, CardAction, CardHeader} from '@camunda/design-system';
import {featureFlags} from '#/shared/feature-flags';
import styles from './TaskDetailsProcessSkeleton.module.scss';

const TaskDetailsProcessSkeleton: React.FC = () => {
	// Mirrors ProcessDiagramView's structure for each path so the loading
	// state and the loaded diagram share the same card surface and header
	// placement — otherwise either the card or the title/tag row would jump
	// position once the diagram resolves.
	if (featureFlags.dsTasklistUI) {
		return (
			<div className={styles.container} data-testid="process-tab-content">
				<Card className={styles.diagramCard}>
					<CardHeader className={styles.diagramCardHeaderDS}>
						<SkeletonText className={styles.title} />
						<CardAction>
							<SkeletonText className={styles.tag} />
						</CardAction>
					</CardHeader>
				</Card>
			</div>
		);
	}

	return (
		<div className={styles.container} data-testid="process-tab-content">
			<div className={styles.header}>
				<SkeletonText className={styles.title} />
				<SkeletonText className={styles.tag} />
			</div>
			<div className={styles.diagram} />
		</div>
	);
};

export {TaskDetailsProcessSkeleton};
