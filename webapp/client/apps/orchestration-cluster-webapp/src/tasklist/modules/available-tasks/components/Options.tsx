/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Toggle} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import styles from './Options.module.scss';

const Options: React.FC = () => {
	const {t} = useTranslation();

	return (
		<section className={styles.container} aria-label={t('tasklist.taskOptionsSectionAria')}>
			<Toggle
				id="toggle-auto-select-task"
				size="sm"
				labelText={t('tasklist.taskOptionsAutoSelectLabel')}
				hideLabel
				labelA={t('tasklist.taskOptionsAutoSelectOffAria')}
				labelB={t('tasklist.taskOptionsAutoSelectOnAria')}
				toggled={false}
			/>
		</section>
	);
};

export {Options};
