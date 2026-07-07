/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Toggle} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import styles from './AutoSelectNextTaskToggle.module.scss';
import {useCallback, useState} from 'react';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';

const AutoSelectNextTaskToggle: React.FC = () => {
	const {t} = useTranslation();
	const [isAutoSelectEnabled, setIsAutoSelectEnabled] = useState(
		() => getStateLocally('tasklist.autoSelectNextTask') ?? false,
	);

	const handleToggle = useCallback((checked: boolean) => {
		storeStateLocally('tasklist.autoSelectNextTask', checked);
		setIsAutoSelectEnabled(checked);
	}, []);

	return (
		<section className={styles.container} aria-label={t('tasklist.taskOptionsSectionAria')}>
			<Toggle
				id="toggle-auto-select-task"
				size="sm"
				labelText={t('tasklist.taskOptionsAutoSelectLabel')}
				hideLabel
				labelA={t('tasklist.taskOptionsAutoSelectOffAria')}
				labelB={t('tasklist.taskOptionsAutoSelectOnAria')}
				toggled={isAutoSelectEnabled}
				onToggle={handleToggle}
			/>
		</section>
	);
};

export {AutoSelectNextTaskToggle};
