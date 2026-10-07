/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge} from '@camunda/design-system';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {CircleDashed, CircleUserRound, UserRound} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {TruncatedText} from './TruncatedText';

type Props = {
	currentUser: CurrentUser;
	assignee: string | null | undefined;
	isShortFormat?: boolean;
	/** Clip a long assignee with an ellipsis and reveal it in a tooltip. Used by the task tiles only. */
	truncate?: boolean;
};

const BADGE_CLASS_NAME = 'min-w-0 shrink [&>svg]:shrink-0';

const AssigneeBadge: React.FC<Props> = ({currentUser, assignee, isShortFormat = true, truncate = false}) => {
	const {t} = useTranslation();
	const isAssigned = typeof assignee === 'string';
	const isAssignedToCurrentUser = assignee === currentUser.username;
	const className = truncate ? BADGE_CLASS_NAME : undefined;

	if (!isAssigned) {
		return (
			<Badge className={className} title={t('tasklist.assigneeTagUnassignedTitle')}>
				<CircleDashed aria-hidden />
				{t('tasklist.assigneeTagUnassigned')}
			</Badge>
		);
	}

	if (isAssignedToCurrentUser) {
		return (
			<Badge className={className} title={t('tasklist.assigneeTagAssignedToMeAria')}>
				<CircleUserRound aria-hidden />
				{isShortFormat ? t('tasklist.assigneeTagAssignedToMeShortForm') : t('tasklist.assigneeTagAssignedToMe')}
			</Badge>
		);
	}

	const assigneeText = isShortFormat ? assignee : t('tasklist.assigneeTagAssignedToX', {assignee});

	if (truncate) {
		return (
			<Badge className={className}>
				<UserRound aria-hidden />
				<TruncatedText variant="label-sm" className="font-medium">
					{assigneeText}
				</TruncatedText>
			</Badge>
		);
	}

	return (
		<Badge title={t('tasklist.assigneeTagAssignedToXAria', {assignee})}>
			<UserRound aria-hidden />
			{assigneeText}
		</Badge>
	);
};

export {AssigneeBadge};
