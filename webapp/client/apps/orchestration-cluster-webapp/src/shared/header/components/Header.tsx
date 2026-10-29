/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {t} from 'i18next';
import {observer} from 'mobx-react-lite';
import {C3Navigation} from '@camunda/camunda-composite-components';
import type {CurrentUser, License} from '@camunda/camunda-api-zod-schemas/8.11';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {UserMenu} from './UserMenu';
import {Link} from '@tanstack/react-router';
import {useNavbar} from '#/shared/header/useNavbar';

function getInfoSidebarItems(isPaidPlan: boolean) {
	const BASE_INFO_SIDEBAR_ITEMS = [
		{
			key: 'docs',
			label: t('headerSidebarDocumentationLink'),
			onClick: () => {
				window.open('https://docs.camunda.io/', '_blank');
			},
		},
		{
			key: 'academy',
			label: t('headerSidebarCamundaAcademyLink'),
			onClick: () => {
				window.open('https://academy.camunda.com/', '_blank');
			},
		},
	];

	const FEEDBACK_AND_SUPPORT_ITEM = {
		key: 'feedbackAndSupport',
		label: t('headerSidebarFeedbackAndSupportLink'),
		onClick: () => {
			window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
		},
	} as const;

	const COMMUNITY_FORUM_ITEM = {
		key: 'communityForum',
		label: t('headerSidebarCommunityForumLink'),
		onClick: () => {
			window.open('https://forum.camunda.io', '_blank');
		},
	};

	return isPaidPlan
		? [...BASE_INFO_SIDEBAR_ITEMS, FEEDBACK_AND_SUPPORT_ITEM, COMMUNITY_FORUM_ITEM]
		: [...BASE_INFO_SIDEBAR_ITEMS, COMMUNITY_FORUM_ITEM];
}

type Props = {
	currentUser: CurrentUser;
	license: License;
};

const Header: React.FC<Props> = observer(({currentUser, license}) => {
	const {t} = useTranslation();
	const isSaas = getBootConfig().organizationId !== null;
	const {canLogout} = getClientConfig().authentication;
	const {displayName, salesPlanType} = currentUser;
	const [isAppBarOpen, setIsAppBarOpen] = useState(false);
	const {app, elements} = useNavbar(currentUser);

	const logoutWithNotification = () => {
		notificationsStore.displayNotification({
			kind: 'info',
			title: t('notificationLogOutTitle'),
			subtitle: t('notificationLogOutSubtitle'),
			isDismissable: true,
		});
		setTimeout(authenticationStore.handleLogout, 1000);
	};

	return (
		<C3Navigation
			toggleAppbar={(open) => setIsAppBarOpen(open)}
			notificationSideBar={isSaas ? {} : undefined}
			appBar={{
				ariaLabel: t('headerAppBarLabel'),
				isOpen: isAppBarOpen,
				appTeaserRouteProps: isSaas ? {} : undefined,
				elements: isSaas ? undefined : [],
			}}
			app={app}
			// @ts-expect-error - C3 types don't support Tanstack Router links
			forwardRef={Link}
			navbar={{
				elements,
				licenseTag: {
					show: license.licenseType !== 'saas',
					isProductionLicense: license.validLicense,
					isCommercial: license.isCommercial,
					expiresAt: license.expiresAt ?? undefined,
				},
			}}
			infoSideBar={{
				isOpen: false,
				ariaLabel: t('headerInfoLabel'),
				elements: getInfoSidebarItems(['paid-cc', 'enterprise'].includes(salesPlanType ?? '')),
			}}
			actionButtons={[
				{
					element: (
						<UserMenu
							key="user-menu"
							userName={displayName ?? currentUser.username}
							canLogout={canLogout}
							onLogout={logoutWithNotification}
						/>
					),
				},
			]}
		/>
	);
});

export {Header};
