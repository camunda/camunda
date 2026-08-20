/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {Search} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import styles from './NoTasks.module.scss';

const NoTasks: React.FC = () => {
	const {t} = useTranslation();

	return (
		<Stack gap={5} orientation="horizontal" className={styles.container}>
			<Search size={24} aria-hidden className={styles.icon} />
			<Stack gap={1} className={styles.text}>
				<span className={styles.heading}>{t('tasklist.availableTasksNoTasksFoundInfo')}</span>
				<span className={styles.body}>{t('tasklist.availableTasksNoTasksMatchingCriteriaInfo')}</span>
			</Stack>
		</Stack>
	);
};

export {NoTasks};
