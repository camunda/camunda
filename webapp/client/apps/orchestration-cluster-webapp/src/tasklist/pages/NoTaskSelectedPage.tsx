/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, EmptyState} from '@camunda/design-system';
import {Check, ListTodo} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {getStateLocally} from '#/shared/browser-storage/local-storage';

const TUTORIAL_URL = 'https://modeler.cloud.camunda.io/tutorial/quick-start-human-tasks';

type Props = {
	hasNoTasks: boolean;
};

const NoTaskSelectedPage: React.FC<Props> = ({hasNoTasks}) => {
	const {t} = useTranslation();
	const hasCompletedTask = getStateLocally('tasklist.hasCompletedTask') === true;

	if (hasNoTasks && hasCompletedTask) {
		return null;
	}

	return (
		<div className="flex h-full items-center justify-center">
			<EmptyState
				icon={hasCompletedTask ? <ListTodo aria-hidden /> : <Check aria-hidden />}
				heading={t(hasCompletedTask ? 'tasklist.taskEmptyPickPrompt' : 'tasklist.taskEmptyHeader')}
				description={
					hasCompletedTask ? undefined : (
						<>
							{t('tasklist.taskEmptyDetail1')} {t('tasklist.taskEmptyDetail2')}
							{hasNoTasks ? null : <> {t('tasklist.taskEmptyTaskAvailablePrompt')}</>}
						</>
					)
				}
				action={
					hasCompletedTask ? undefined : (
						<Button asChild>
							<a href={TUTORIAL_URL} target="_blank" rel="noreferrer">
								{t('tasklist.taskEmptyTutorialCta')}
							</a>
						</Button>
					)
				}
			/>
		</div>
	);
};

export {NoTaskSelectedPage};
