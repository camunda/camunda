/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HelpCircle} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@camunda/design-system';

type Props = {
	isPaidPlan: boolean;
};

// DS-only. Replaces C3Navigation's infoSideBar with a plain DS DropdownMenu —
// infoSideBar is a composite with no carbon-compat adapter, and the old
// content was already just a flat list of external links, which a dropdown
// reproduces without a dedicated sidebar surface.
const HelpMenu: React.FC<Props> = ({isPaidPlan}) => {
	const {t} = useTranslation();

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button variant="ghost" size="icon" aria-label={t('headerInfoLabel')}>
					<HelpCircle aria-hidden />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end">
				<DropdownMenuItem
					onSelect={() => {
						window.open('https://docs.camunda.io/', '_blank', 'noopener,noreferrer');
					}}
				>
					{t('headerSidebarDocumentationLink')}
				</DropdownMenuItem>
				<DropdownMenuItem
					onSelect={() => {
						window.open('https://academy.camunda.com/', '_blank', 'noopener,noreferrer');
					}}
				>
					{t('headerSidebarCamundaAcademyLink')}
				</DropdownMenuItem>
				{isPaidPlan ? (
					<DropdownMenuItem
						onSelect={() => {
							window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank', 'noopener,noreferrer');
						}}
					>
						{t('headerSidebarFeedbackAndSupportLink')}
					</DropdownMenuItem>
				) : null}
				<DropdownMenuItem
					onSelect={() => {
						window.open('https://forum.camunda.io', '_blank', 'noopener,noreferrer');
					}}
				>
					{t('headerSidebarCommunityForumLink')}
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {HelpMenu};
