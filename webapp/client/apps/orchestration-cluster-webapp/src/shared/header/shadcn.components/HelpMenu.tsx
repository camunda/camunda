/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@camunda/design-system';
import {HelpCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';

type Props = {
	isPaidPlan: boolean;
};

const HelpMenu: React.FC<Props> = ({isPaidPlan}) => {
	const {t} = useTranslation();

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button type="button" variant="ghost" size="icon" aria-label={t('headerInfoLabel')}>
					<HelpCircle aria-hidden />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end">
				<DropdownMenuItem asChild>
					<a href="https://docs.camunda.io/" target="_blank" rel="noopener noreferrer">
						{t('headerSidebarDocumentationLink')}
					</a>
				</DropdownMenuItem>
				<DropdownMenuItem asChild>
					<a href="https://academy.camunda.com/" target="_blank" rel="noopener noreferrer">
						{t('headerSidebarCamundaAcademyLink')}
					</a>
				</DropdownMenuItem>
				{isPaidPlan ? (
					<DropdownMenuItem asChild>
						<a href="https://jira.camunda.com/projects/SUPPORT/queues" target="_blank" rel="noopener noreferrer">
							{t('headerSidebarFeedbackAndSupportLink')}
						</a>
					</DropdownMenuItem>
				) : null}
				<DropdownMenuItem asChild>
					<a href="https://forum.camunda.io" target="_blank" rel="noopener noreferrer">
						{t('headerSidebarCommunityForumLink')}
					</a>
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {HelpMenu};
