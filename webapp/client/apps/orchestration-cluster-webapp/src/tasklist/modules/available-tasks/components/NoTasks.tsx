/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack, SearchIcon} from '#/shared/design-system-compat';
import {useTranslation} from 'react-i18next';
import {Search} from 'lucide-react';
import {EmptyState} from '@camunda/design-system';
import {featureFlags} from '#/shared/feature-flags';
import styles from './NoTasks.module.scss';

const NoTasks: React.FC = () => {
	const {t} = useTranslation();

	// DS-only: the DS's own EmptyState component (size="sm", no action — per
	// explicit request) instead of the hand-rolled Stack/icon/heading/body
	// markup below, which stays Legacy-only.
	if (featureFlags.dsTasklistUI) {
		return (
			<EmptyState
				size="sm"
				icon={<Search aria-hidden />}
				heading={t('tasklist.availableTasksNoTasksFoundInfo')}
				description={t('tasklist.availableTasksNoTasksMatchingCriteriaInfo')}
			/>
		);
	}

	return (
		<Stack gap={5} orientation="horizontal" className={styles.container}>
			<SearchIcon size={24} aria-hidden className={styles.icon} />
			<Stack gap={1} className={styles.text}>
				<span className={styles.heading}>{t('tasklist.availableTasksNoTasksFoundInfo')}</span>
				<span className={styles.body}>{t('tasklist.availableTasksNoTasksMatchingCriteriaInfo')}</span>
			</Stack>
		</Stack>
	);
};

export {NoTasks};
