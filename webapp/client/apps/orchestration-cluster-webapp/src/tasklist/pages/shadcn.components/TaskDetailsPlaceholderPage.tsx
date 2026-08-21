/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EmptyState} from '@camunda/design-system';
import {ListTodo} from 'lucide-react';
import {useTranslation} from 'react-i18next';

type Props = {
	userTaskKey: string;
};

const TaskDetailsPlaceholderPage: React.FC<Props> = ({userTaskKey}) => {
	const {t} = useTranslation();

	return (
		<div className="flex min-h-full items-center justify-center p-6">
			<EmptyState
				icon={<ListTodo aria-hidden />}
				heading={t('tasklist.taskDetailsDetailsLabel')}
				description={userTaskKey}
			/>
		</div>
	);
};

export {TaskDetailsPlaceholderPage};
