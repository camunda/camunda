/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText} from '@carbon/react';
import styles from './TaskDetailsProcessSkeleton.module.scss';

const TaskDetailsProcessSkeleton: React.FC = () => {
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
