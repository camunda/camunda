/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';

type Props = {
	taskName: string;
	processName: string;
};

const TaskDetailsHeader: React.FC<Props> = ({taskName, processName}) => {
	const {t} = useTranslation();

	return (
		<header
			className="flex w-full flex-wrap items-center justify-between gap-4 px-4 pb-4"
			title={t('tasklist.taskDetailsHeader')}
		>
			<div className="flex min-w-0 flex-col">
				<Text variant="label-md-strong" className="truncate text-neutral-foreground-strong">
					{taskName}
				</Text>
				<Text variant="helper" className="truncate text-neutral-foreground-subtle">
					{processName}
				</Text>
			</div>
		</header>
	);
};

export {TaskDetailsHeader};
