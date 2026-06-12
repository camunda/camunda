/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {CollapsiblePanel} from '#/tasklist/modules/available-tasks/components/CollapsiblePanel';
import {Filters} from '#/tasklist/modules/available-tasks/components/Filters';
import {NoTasks} from '#/tasklist/modules/available-tasks/components/NoTasks';
import {Options} from '#/tasklist/modules/available-tasks/components/Options';
import styles from './TasklistIndexPage.module.scss';

const TasklistIndexPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<main className={styles.container}>
			<CollapsiblePanel />
			<Stack as="section" className={styles.tasksPanel} aria-label={t('tasksPanelLabel')}>
				<Filters />
				<div className={styles.tasksContainer} title={t('availableTasksTitle')}>
					<NoTasks />
				</div>
				<Options />
			</Stack>
			<div className={styles.detailsPanel} />
		</main>
	);
};

export {TasklistIndexPage};
