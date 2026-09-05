/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EmptyState} from '@camunda/design-system';
import {Search} from 'lucide-react';
import {useTranslation} from 'react-i18next';

const NoTasks: React.FC = () => {
	const {t} = useTranslation();

	return (
		<div className="p-4">
			<EmptyState
				size="sm"
				icon={<Search aria-hidden />}
				heading={t('tasklist.availableTasksNoTasksFoundInfo')}
				description={t('tasklist.availableTasksNoTasksMatchingCriteriaInfo')}
			/>
		</div>
	);
};

export {NoTasks};
