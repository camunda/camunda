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
	isShortFormat?: boolean;
};

const AssigneeBadge: React.FC<Props> = ({currentUser, assignee, isShortFormat = true}) => {
	const {t} = useTranslation();
	const isAssigned = typeof assignee === 'string';
	const isAssignedToCurrentUser = assignee === currentUser.username;

	if (!isAssigned) {
		return (
			<Badge
				className="border-transparent bg-transparent text-neutral-foreground-subtle"
				title={t('tasklist.assigneeTagUnassignedTitle')}
			>
				<CircleDashed aria-hidden />
				{t('tasklist.assigneeTagUnassigned')}
			</Badge>
		);
	}

	if (isAssignedToCurrentUser) {
		return (
			<Badge
				className="border-transparent bg-neutral-background-strong text-neutral-foreground-strong"
				title={t('tasklist.assigneeTagAssignedToMeAria')}
			>
				<CircleUserRound aria-hidden />
				{isShortFormat ? t('tasklist.assigneeTagAssignedToMeShortForm') : t('tasklist.assigneeTagAssignedToMe')}
			</Badge>
		);
	}

	return (
		<Badge
			className="border-transparent bg-transparent text-neutral-foreground-strong"
			title={t('tasklist.assigneeTagAssignedToXAria', {assignee})}
		>
			<UserRound aria-hidden />
			{isShortFormat ? assignee : t('tasklist.assigneeTagAssignedToX', {assignee})}
		</Badge>
	);
};

export {AssigneeBadge};
