/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@camunda/design-system';
import {HelpCircle} from 'lucide-react';
import {useCallback} from 'react';
import {useTranslation} from 'react-i18next';

type Props = {
	isPaidPlan: boolean;
};

const HelpMenu: React.FC<Props> = ({isPaidPlan}) => {
	const {t} = useTranslation();
	const handleDocumentationClick = useCallback(() => {
		window.open('https://docs.camunda.io/', '_blank', 'noopener,noreferrer');
	}, []);
	const handleAcademyClick = useCallback(() => {
		window.open('https://academy.camunda.com/', '_blank', 'noopener,noreferrer');
	}, []);
	const handleFeedbackAndSupportClick = useCallback(() => {
		window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank', 'noopener,noreferrer');
	}, []);
	const handleCommunityForumClick = useCallback(() => {
		window.open('https://forum.camunda.io', '_blank', 'noopener,noreferrer');
	}, []);

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button type="button" variant="ghost" size="icon" aria-label={t('headerInfoLabel')}>
					<HelpCircle aria-hidden />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end">
				<DropdownMenuItem onClick={handleDocumentationClick}>{t('headerSidebarDocumentationLink')}</DropdownMenuItem>
				<DropdownMenuItem onClick={handleAcademyClick}>{t('headerSidebarCamundaAcademyLink')}</DropdownMenuItem>
				{isPaidPlan ? (
					<DropdownMenuItem onClick={handleFeedbackAndSupportClick}>
						{t('headerSidebarFeedbackAndSupportLink')}
					</DropdownMenuItem>
				) : null}
				<DropdownMenuItem onClick={handleCommunityForumClick}>{t('headerSidebarCommunityForumLink')}</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {HelpMenu};
