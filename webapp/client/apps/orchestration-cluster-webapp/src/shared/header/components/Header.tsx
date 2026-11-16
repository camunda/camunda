/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@camunda/design-system/styles.css';
import './Header.css';
import {useLayoutEffect, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {Link} from '@tanstack/react-router';
import {
	preview_C3ToolsArea as C3ToolsArea,
	preview_useCamundaTools as useCamundaTools,
	type UseCamundaToolsOptions,
} from '@camunda/camunda-composite-components';
import {
	AppHeader as DsAppHeader,
	AppSidebar,
	C4Provider,
	CamundaLogo,
	Label,
	RadioGroup,
	RadioGroupItem,
	SidebarProvider,
	Text,
	TooltipProvider,
	useSidebar,
	type UserMenuItem,
} from '@camunda/design-system';
import type {AuthenticatedCurrentUser, License} from '@camunda/camunda-api-zod-schemas/8.11';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getC4Dictionary, getC4Locale} from '#/shared/i18n/shadcn.components/c4-dictionaries';
import {themeStore} from '#/shared/theme/theme';
import {HeaderBadges} from './HeaderBadges';
import {InfoMenu} from './InfoMenu';
import {LogoutAwareUserMenu} from './LogoutAwareUserMenu';
import {useBreadcrumbs} from './useBreadcrumbs';
import {useCurrentPage} from './useCurrentPage';
import {useSidebarChildren} from './useSidebarChildren';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';
const CURRENT_YEAR = new Date().getFullYear();
const LOGOUT_DELAY = 1000;

const THEME_OPTIONS = [
	{value: 'light', labelKey: 'tasklist.headerThemeLight'},
	{value: 'system', labelKey: 'tasklist.headerThemeSystem'},
	{value: 'dark', labelKey: 'tasklist.headerThemeDark'},
] as const;

const SidebarBodySync: React.FC<{hasSidebar: boolean}> = ({hasSidebar}) => {
	const ctx = useSidebar();
	const sidebarWidth = !hasSidebar || !ctx || ctx.isMobile ? '0px' : ctx.expanded ? ctx.width : '3rem';

	useLayoutEffect(() => {
		document.body.style.setProperty('--app-sidebar-width', sidebarWidth);
		return () => {
			document.body.style.removeProperty('--app-sidebar-width');
		};
	}, [sidebarWidth]);

	return null;
};

const ThemeSelector: React.FC = observer(() => {
	const {t} = useTranslation();
	const {selectedTheme, changeTheme} = themeStore;

	return (
		<div className="px-2 py-1.5">
			<Text as="span" variant="label-sm" className="text-neutral-foreground-subtle">
				{t('tasklist.headerThemeLabel')}
			</Text>
			<RadioGroup
				aria-label={t('tasklist.headerThemeLabel')}
				value={selectedTheme}
				onValueChange={(value) => {
					changeTheme(value as typeof selectedTheme);
				}}
				className="mt-2 gap-2"
			>
				{THEME_OPTIONS.map((option) => (
					<Label key={option.value} className="flex items-center gap-2">
						<RadioGroupItem value={option.value} />
						{t(option.labelKey)}
					</Label>
				))}
			</RadioGroup>
		</div>
	);
});

const VersionFooter: React.FC = () => {
	const {t} = useTranslation();

	return (
		<div className="px-2 py-1.5 text-xs text-neutral-foreground-subtle">
			<div>{t('headerVersionLabel', {version: import.meta.env.VITE_VERSION})}</div>
			<div>© Camunda Services GmbH {CURRENT_YEAR}. All rights reserved.</div>
		</div>
	);
};

type Props = {
	currentUser: AuthenticatedCurrentUser;
	license: License;
};

const Header: React.FC<Props> = observer(({currentUser, license}) => {
	const {t, i18n} = useTranslation();
	const locale = getC4Locale(i18n.resolvedLanguage);
	const {currentPage} = useCurrentPage();
	const sidebarChildren = useSidebarChildren(currentUser);
	const hasSidebar = sidebarChildren.length > 0;
	const {canLogout} = getClientConfig().authentication;
	const isSaas = getBootConfig().organizationId !== null;
	const toolsOptions = useMemo(
		() => ({
			notifications: isSaas ? {title: 'Notifications'} : undefined,
		}),
		[isSaas],
	) satisfies UseCamundaToolsOptions;
	const {tools, ToolsProvider} = useCamundaTools(toolsOptions);
	const breadcrumb = useBreadcrumbs({isSaas, webappLinks: currentUser.c8Links});
	const isPaidPlan = ['paid-cc', 'enterprise'].includes(currentUser.salesPlanType ?? '');
	const userMenuItems: UserMenuItem[] = [
		...(window.Osano?.cm === undefined
			? []
			: [
					{
						key: 'cookie',
						label: t('headerCookiePreferencesLabel'),
						onClick: () => {
							window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
						},
					},
				]),
		{
			key: 'terms',
			label: t('headerTermsOfUseLabel'),
			onClick: () => {
				window.open('https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/', '_blank');
			},
		},
		{
			key: 'privacy',
			label: t('headerPrivacyPolicyLabel'),
			onClick: () => {
				window.open('https://camunda.com/legal/privacy/', '_blank');
			},
		},
		{
			key: 'imprint',
			label: t('headerImprintLabel'),
			onClick: () => {
				window.open('https://camunda.com/legal/imprint/', '_blank');
			},
		},
	];

	return (
		<ToolsProvider>
			<C4Provider theme={themeStore.actualTheme} dictionary={getC4Dictionary(locale)} locale={locale}>
				<TooltipProvider>
					<SidebarProvider>
						<SidebarBodySync hasSidebar={hasSidebar} />
						<DsAppHeader
							skipToContentTargetId={SKIP_TO_CONTENT_TARGET_ID}
							showSidebarTrigger={hasSidebar}
							logo={
								<Link to="/operate" aria-label="Camunda Operate">
									<CamundaLogo className="operate-nav-v2-logo block" />
								</Link>
							}
							breadcrumb={breadcrumb}
							trailing={<HeaderBadges license={license} />}
							globalActions={[
								...(isSaas
									? [
											{
												key: 'notifications',
												label: 'Notifications',
												element: <C3ToolsArea tools={tools} />,
											},
										]
									: []),
								{
									key: 'info',
									label: t('headerInfoLabel'),
									element: <InfoMenu isPaidPlan={isPaidPlan} />,
								},
							]}
							actions={
								<LogoutAwareUserMenu
									userName={currentUser.displayName ?? currentUser.username}
									userEmail={currentUser.email ?? ''}
									canLogout={canLogout}
									customSection={
										<>
											<ThemeSelector />
											<VersionFooter />
										</>
									}
									items={userMenuItems}
									onLogout={() => {
										notificationsStore.displayNotification({
											kind: 'info',
											title: t('notificationLogOutTitle'),
											subtitle: t('notificationLogOutSubtitle'),
											isDismissable: true,
										});
										setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
									}}
								/>
							}
						/>
						{hasSidebar && (
							<AppSidebar
								ariaLabel="Camunda Operate"
								items={sidebarChildren}
								activeItemKey={currentPage ?? ''}
								linkComponent={Link}
							/>
						)}
					</SidebarProvider>
				</TooltipProvider>
			</C4Provider>
		</ToolsProvider>
	);
});

export {Header};
