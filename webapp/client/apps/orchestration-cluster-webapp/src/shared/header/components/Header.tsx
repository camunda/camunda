/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@camunda/design-system/styles.css';
import './Header.css';
import {useCallback, useLayoutEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {Link} from '@tanstack/react-router';
import {
	AppHeader,
	AppSidebar,
	C4Provider,
	CamundaLogo,
	SidebarProvider,
	TooltipProvider,
	useSidebar,
} from '@camunda/design-system';
import type {AuthenticatedCurrentUser} from '@camunda/camunda-api-zod-schemas/8.11';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getC4Dictionary, getC4Locale} from '#/shared/i18n/shadcn.components/c4-dictionaries';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {themeStore} from '#/shared/theme/theme';
import {AccountMenu} from '#/shared/header/shadcn.components/AccountMenu';
import {HeaderBadges} from '#/shared/header/shadcn.components/HeaderBadges';
import {HelpMenu} from '#/shared/header/shadcn.components/HelpMenu';
import {SaasAccountMenu} from '#/shared/header/shadcn.components/SaasAccountMenu';
import {useSaasGlobalActions} from '#/shared/header/shadcn.components/useSaasGlobalActions';
import {useOperateSidebarNavigation} from '#/shared/header/useOperateSidebarNavigation';
import {Breadcrumbs} from '#/shared/header/shadcn.components/Breadcrumbs';

const SIDEBAR_COLLAPSED_WIDTH = '3.5rem';
const LOGOUT_DELAY = 1000;

function getSidebarWidth(hasSidebar: boolean, sidebar: ReturnType<typeof useSidebar>) {
	if (!hasSidebar || sidebar === null || sidebar.isMobile) {
		return '0px';
	}

	return sidebar.expanded ? sidebar.width : SIDEBAR_COLLAPSED_WIDTH;
}

const SidebarWidthSync: React.FC<{hasSidebar: boolean}> = ({hasSidebar}) => {
	const sidebar = useSidebar();
	const width = getSidebarWidth(hasSidebar, sidebar);

	useLayoutEffect(() => {
		document.body.style.setProperty('--app-sidebar-width', width);

		return () => {
			document.body.style.removeProperty('--app-sidebar-width');
		};
	}, [width]);

	return null;
};

type Props = {
	currentUser: AuthenticatedCurrentUser;
	initialSaasToken: string | null;
	children: React.ReactNode;
};

const Header: React.FC<Props> = observer(({currentUser, initialSaasToken, children}) => {
	const {t, i18n} = useTranslation();
	const items = useOperateSidebarNavigation(currentUser);
	const {canLogout} = getClientConfig().authentication;
	const {isSaas, globalActions} = useSaasGlobalActions(initialSaasToken);
	const locale = getC4Locale(i18n.resolvedLanguage);
	const displayName = currentUser.displayName ?? currentUser.username;

	const handleLogout = useCallback(() => {
		notificationsStore.displayNotification({
			kind: 'info',
			title: t('notificationLogOutTitle'),
			subtitle: t('notificationLogOutSubtitle'),
			isDismissable: true,
		});
		setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
	}, [t]);

	const handleCookiePreferences =
		window.Osano?.cm !== undefined
			? () => {
					window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
				}
			: undefined;

	const accountMenuProps = {
		displayName,
		email: currentUser.email,
		version: import.meta.env.VITE_VERSION,
		canLogout,
		onLogout: handleLogout,
		onOpenCookiePreferences: handleCookiePreferences,
	};

	return (
		<>
			<C4Provider theme={themeStore.actualTheme} dictionary={getC4Dictionary(locale)} locale={locale}>
				<TooltipProvider>
					<SidebarProvider collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}>
						<SidebarWidthSync hasSidebar={items.length > 0} />
						<AppHeader
							skipToContentTargetId="main-content"
							showSidebarTrigger={items.length > 0}
							logo={
								<Link aria-label={t('loginLogoLabel')} className="flex items-center" to="/operate">
									<CamundaLogo className="operate-header-logo block" />
								</Link>
							}
							breadcrumb={
								<Breadcrumbs
									currentApp="operate"
									webappLinks={currentUser.c8Links}
									operateBase="/operate"
									className="operate-header-breadcrumb"
								/>
							}
							trailing={<HeaderBadges hideLicenseBelow="48rem" />}
							globalActions={globalActions}
							actions={
								<>
									<HelpMenu isPaidPlan={['paid-cc', 'enterprise'].includes(currentUser.salesPlanType ?? '')} />
									{isSaas ? <SaasAccountMenu {...accountMenuProps} /> : <AccountMenu {...accountMenuProps} />}
								</>
							}
						/>
						{items.length > 0 && (
							<AppSidebar
								ariaLabel={t('headerAppBarLabel')}
								items={items}
								linkComponent={Link}
								resizable={false}
								collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
							/>
						)}
					</SidebarProvider>
				</TooltipProvider>
			</C4Provider>
			<main id="main-content" tabIndex={-1} className="operate-page-content">
				{children}
			</main>
		</>
	);
});

export {Header};
