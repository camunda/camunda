/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Card, CardDescription, CardHeader, CardTitle, Spinner} from '@camunda/design-system';
import type {CurrentUser, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {CaseTaskPanel} from '#/tasklist/modules/case-details/components/CaseTaskPanel';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	formSchema: string | null;
	variables: Variable[];
	isSearchingNextWork: boolean;
	onCompleted: (userTaskKey: string) => void;
	onClose: () => void;
};

const TasklistCaseTaskPage: React.FC<Props> = ({
	task,
	currentUser,
	formSchema,
	variables,
	isSearchingNextWork,
	onCompleted,
	onClose,
}) => {
	const {t} = useTranslation();

	if (isSearchingNextWork) {
		return (
			<Card>
				<CardHeader>
					<CardDescription className="text-xs font-semibold tracking-wide uppercase">
						{t('tasklist.caseTaskCompleted', {name: task.name || task.elementId})}
					</CardDescription>
					<CardTitle className="flex items-center gap-2 text-lg">
						<Spinner />
						{t('tasklist.caseSearchingNextWork')}
					</CardTitle>
				</CardHeader>
			</Card>
		);
	}

	return (
		<CaseTaskPanel
			key={task.userTaskKey}
			task={task}
			currentUser={currentUser}
			formSchema={formSchema}
			variables={variables}
			onCompleted={onCompleted}
			onClose={onClose}
		/>
	);
};

export {TasklistCaseTaskPage};
