/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {
	CircleDashIcon,
	Tag as BaseTag,
	type TagProps,
	UserAvatarFilledIcon,
	UserAvatarIcon,
} from '#/shared/design-system-compat';
import {cn} from '#/shared/cn';
import styles from './AssigneeTag.module.scss';

type Props = {
	currentUser: CurrentUser;
	assignee: string | null | undefined;
	isShortFormat?: boolean;
};

type AssigneeTagProps = {
	$isHighlighted?: boolean;
	$isAssigned?: boolean;
	children: React.ReactNode;
};

const Tag: React.FC<TagProps<'div'> & AssigneeTagProps> = ({
	className = '',
	children,
	$isHighlighted,
	$isAssigned,
	size,
	...rest
}) => (
	<BaseTag
		size={size}
		{...rest}
		className={cn(className, styles.tag, {
			[styles.assigned!]: $isAssigned,
			[styles.highlighted!]: $isHighlighted,
			[styles.small!]: size == 'sm',
		})}
	>
		{children}
	</BaseTag>
);

const AssigneeTag: React.FC<Props> = ({currentUser, assignee, isShortFormat = true}) => {
	const {t} = useTranslation();
	const {username} = currentUser;
	const isAssigned = typeof assignee === 'string';
	const isAssignedToCurrentUser = assignee === username;

	if (!isAssigned) {
		return (
			<Tag title={t('tasklist.assigneeTagUnassignedTitle')} size={isShortFormat ? 'sm' : 'md'} unselectable="off">
				<CircleDashIcon size={16} />
				{t('tasklist.assigneeTagUnassigned')}
			</Tag>
		);
	}
	if (isAssignedToCurrentUser) {
		return (
			<Tag
				$isHighlighted
				$isAssigned
				title={t('tasklist.assigneeTagAssignedToMeAria')}
				size={isShortFormat ? 'sm' : 'md'}
				unselectable="off"
			>
				<UserAvatarFilledIcon size={16} />
				{isShortFormat ? t('tasklist.assigneeTagAssignedToMeShortForm') : t('tasklist.assigneeTagAssignedToMe')}
			</Tag>
		);
	}
	return (
		<Tag
			$isAssigned
			title={t('tasklist.assigneeTagAssignedToXAria', {assignee})}
			size={isShortFormat ? 'sm' : 'md'}
			unselectable="off"
		>
			<UserAvatarIcon size={16} />
			{isShortFormat ? assignee : t('tasklist.assigneeTagAssignedToX', {assignee})}
		</Tag>
	);
};

export {AssigneeTag};
