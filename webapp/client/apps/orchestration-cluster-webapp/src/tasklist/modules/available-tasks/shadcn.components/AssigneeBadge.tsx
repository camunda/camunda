/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge} from '@camunda/design-system';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {CircleDashed, CircleUserRound, UserRound} from 'lucide-react';
import {useTranslation} from 'react-i18next';

type Props = {
	currentUser: CurrentUser;
	assignee: string | null | undefined;
};

const AssigneeBadge: React.FC<Props> = ({currentUser, assignee}) => {
	const {t} = useTranslation();
	const isAssigned = typeof assignee === 'string';
	const isAssignedToCurrentUser = assignee === currentUser.username;

	if (!isAssigned) {
		return (
			<Badge title={t('tasklist.assigneeTagUnassignedTitle')}>
				<CircleDashed aria-hidden />
				{t('tasklist.assigneeTagUnassigned')}
			</Badge>
		);
	}

	if (isAssignedToCurrentUser) {
		return (
			<Badge variant="accent" title={t('tasklist.assigneeTagAssignedToMeAria')}>
				<CircleUserRound aria-hidden />
				{t('tasklist.assigneeTagAssignedToMeShortForm')}
			</Badge>
		);
	}

	return (
		<Badge title={t('tasklist.assigneeTagAssignedToXAria', {assignee})}>
			<UserRound aria-hidden />
			{assignee}
		</Badge>
	);
};

export {AssigneeBadge};
