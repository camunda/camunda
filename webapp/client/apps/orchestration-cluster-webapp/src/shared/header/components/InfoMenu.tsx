/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {CircleHelp} from '@camunda/design-system/icons';
import {
	Button,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuTrigger,
} from '@camunda/design-system';

const INFO_ITEMS = [
	{key: 'docs', labelKey: 'headerSidebarDocumentationLink', href: 'https://docs.camunda.io/'},
	{key: 'academy', labelKey: 'headerSidebarCamundaAcademyLink', href: 'https://academy.camunda.com/'},
] as const;

const FEEDBACK_AND_SUPPORT_ITEM = {
	key: 'feedbackAndSupport',
	labelKey: 'headerSidebarFeedbackAndSupportLink',
	href: 'https://jira.camunda.com/projects/SUPPORT/queues',
} as const;

const COMMUNITY_FORUM_ITEM = {
	key: 'communityForum',
	labelKey: 'headerSidebarCommunityForumLink',
	href: 'https://forum.camunda.io',
} as const;

const InfoMenu: React.FC<{isPaidPlan: boolean}> = ({isPaidPlan}) => {
	const {t} = useTranslation();
	const items = useMemo(
		() =>
			isPaidPlan
				? [...INFO_ITEMS, FEEDBACK_AND_SUPPORT_ITEM, COMMUNITY_FORUM_ITEM]
				: [...INFO_ITEMS, COMMUNITY_FORUM_ITEM],
		[isPaidPlan],
	);

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button type="button" variant="ghost" size="icon" aria-label={t('headerInfoLabel')}>
					<CircleHelp aria-hidden className="size-4" />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent className="w-56" align="end">
				<DropdownMenuLabel>{t('headerInfoLabel')}</DropdownMenuLabel>
				{items.map(({key, labelKey, href}) => (
					<DropdownMenuItem key={key} asChild>
						<a href={href} target="_blank" rel="noopener noreferrer">
							{t(labelKey)}
						</a>
					</DropdownMenuItem>
				))}
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {InfoMenu};
