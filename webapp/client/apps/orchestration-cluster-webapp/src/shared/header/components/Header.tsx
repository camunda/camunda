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
import {ArrowRight} from '@carbon/react/icons';
import {C3Navigation} from '@camunda/camunda-composite-components';
import type {CurrentUser, License} from '@camunda/camunda-api-zod-schemas/8.10';
import {themeStore} from '#/shared/theme/theme';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {tracking} from '#/shared/tracking';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {LanguageSelector} from './LanguageSelector';
import {Link} from '@tanstack/react-router';
import {useNavbar} from '../useNavbar';

function getInfoSidebarItems(isPaidPlan: boolean) {
	const BASE_INFO_SIDEBAR_ITEMS = [
		{
			key: 'docs',
			label: t('headerSidebarDocumentationLink'),
			onClick: () => {
				tracking.track({eventName: 'tasklist:info-bar', link: 'documentation'});
				window.open('https://docs.camunda.io/', '_blank');
			},
		},
		{
			key: 'academy',
			label: t('headerSidebarCamundaAcademyLink'),
			onClick: () => {
				tracking.track({eventName: 'tasklist:info-bar', link: 'academy'});
				window.open('https://academy.camunda.com/', '_blank');
			},
		},
	];

	const FEEDBACK_AND_SUPPORT_ITEM = {
		key: 'feedbackAndSupport',
		label: t('headerSidebarFeedbackAndSupportLink'),
		onClick: () => {
			tracking.track({eventName: 'tasklist:info-bar', link: 'feedback'});
			window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
		},
	} as const;

	const COMMUNITY_FORUM_ITEM = {
		key: 'communityForum',
		label: t('headerSidebarCommunityForumLink'),
		onClick: () => {
			tracking.track({eventName: 'tasklist:info-bar', link: 'forum'});
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
	const {selectedTheme, changeTheme} = themeStore;
	const {displayName, salesPlanType} = currentUser;
	const [isAppBarOpen, setIsAppBarOpen] = useState(false);
	const {app, elements} = useNavbar(currentUser);

	return (
		<C3Navigation
			toggleAppbar={(open) => setIsAppBarOpen(open)}
			notificationSideBar={isSaas ? {} : undefined}
			appBar={{
				ariaLabel: t('headerAppBarLabel'),
				isOpen: isAppBarOpen,
				elementClicked: (appName: string) => {
					tracking.track({eventName: 'tasklist:app-switcher-item-clicked', app: appName});
				},
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
			userSideBar={{
				ariaLabel: t('headerSettingsLabel'),
				version: import.meta.env.VITE_VERSION,
				customElements: {
					profile: {
						label: t('headerProfileLabel'),
						user: {
							name: displayName,
							email: '',
						},
					},
					themeSelector: {
						currentTheme: selectedTheme,
						onChange: (theme: string) => {
							changeTheme(theme as 'system' | 'dark' | 'light');
						},
					},
					customSection: <LanguageSelector />,
				},
				elements: [
					...(window.Osano?.cm === undefined
						? []
						: [
								{
									key: 'cookie',
									label: t('headerCookiePreferencesLabel'),
									onClick: () => {
										tracking.track({eventName: 'tasklist:user-side-bar', link: 'cookies'});
										window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
									},
								},
							]),
					{
						key: 'terms',
						label: t('headerTermsOfUseLabel'),
						onClick: () => {
							tracking.track({eventName: 'tasklist:user-side-bar', link: 'terms-conditions'});
							window.open('https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/', '_blank');
						},
					},
					{
						key: 'privacy',
						label: t('headerPrivacyPolicyLabel'),
						onClick: () => {
							tracking.track({eventName: 'tasklist:user-side-bar', link: 'privacy-policy'});
							window.open('https://camunda.com/legal/privacy/', '_blank');
						},
					},
					{
						key: 'imprint',
						label: t('headerImprintLabel'),
						onClick: () => {
							tracking.track({eventName: 'tasklist:user-side-bar', link: 'imprint'});
							window.open('https://camunda.com/legal/imprint/', '_blank');
						},
					},
				],
				bottomElements: canLogout
					? [
							{
								key: 'logout',
								label: t('headerLogOutLabel'),
								renderIcon: ArrowRight,
								kind: 'ghost',
								onClick: () => authenticationStore.handleLogout(),
							},
						]
					: undefined,
			}}
		/>
	);
});

export {Header};
