/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	AppHeader,
	AppSidebar,
	CamundaLogo,
	SidebarProvider,
	toast,
	TooltipProvider,
	useMediaQuery,
} from '@camunda/design-system';
import {useSuspenseQuery} from '@tanstack/react-query';
import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getCloudStage} from '#/shared/config/getCloudStage';
import {getNotificationsUrl} from '#/shared/config/getNotificationsUrl';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';
import {useSidebarNavigation} from '#/shared/header/useSidebarNavigation.shadcn';
import {AccountMenu} from './AccountMenu';
import {useBreadcrumbs} from './useBreadcrumbs';
import {HelpMenu} from './HelpMenu';
import {LicenseBadges} from './LicenseBadges';
import {SaasNotifications} from '#/shared/notifications/shadcn.components/SaasNotifications';
import {useCallback, useMemo} from 'react';

const SIDEBAR_COLLAPSED_WIDTH = '3.5rem';
const SIDEBAR_EXPANDED_WIDTH = '12.25rem';

type Props = {
	children: React.ReactNode;
};

const Header: React.FC<Props> = ({children}) => {
	const {t} = useTranslation();
	const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
	const {data: license} = useSuspenseQuery(queries.getLicense());
	const {ariaLabel, homeRoute, items, product} = useSidebarNavigation(currentUser);
	const {canLogout} = getClientConfig().authentication;
	const {organizationId, clusterId} = getBootConfig();
	const cloudStage = getCloudStage();
	const notificationsUrl = cloudStage === undefined ? undefined : getNotificationsUrl(cloudStage);
	const notificationsConfig = useMemo(
		() =>
			organizationId !== null && clusterId !== null && notificationsUrl !== undefined
				? {organizationId, url: notificationsUrl}
				: undefined,
		[clusterId, notificationsUrl, organizationId],
	);
	const isBelowLg = useMediaQuery('(width < 64rem)');
	const breadcrumb = useBreadcrumbs({webappLinks: currentUser.c8Links});
	const globalActions = useMemo(
		() =>
			notificationsConfig === undefined
				? undefined
				: [
						{
							key: 'notifications',
							label: t('headerNotificationsLabel'),
							element: <SaasNotifications {...notificationsConfig} />,
						},
					],
		[notificationsConfig, t],
	);

	const handleLogout = useCallback(() => {
		toast.info(t('notificationLogOutTitle'), {
			description: t('notificationLogOutSubtitle'),
		});
		setTimeout(authenticationStore.handleLogout, 1000);
	}, [t]);
	const handleCookiePreferences = useMemo(
		() =>
			window.Osano?.cm !== undefined
				? () => {
						window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
					}
				: undefined,
		[],
	);

	return (
		<TooltipProvider>
			<SidebarProvider
				defaultExpanded={false}
				defaultWidth={SIDEBAR_EXPANDED_WIDTH}
				collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
			>
				<div className="h-dvh overflow-hidden bg-background text-neutral-foreground-strong">
					<AppHeader
						skipToContentTargetId="main-content"
						logo={
							<Link aria-label={t('loginLogoLabel')} className="flex items-center" to={homeRoute}>
								<CamundaLogo />
							</Link>
						}
						breadcrumb={product === undefined ? undefined : breadcrumb}
						trailing={isBelowLg ? undefined : <LicenseBadges license={license} />}
						globalActions={globalActions}
						actions={
							<>
								<HelpMenu isPaidPlan={['paid-cc', 'enterprise'].includes(currentUser.salesPlanType ?? '')} />
								<AccountMenu
									displayName={currentUser.displayName}
									canLogout={canLogout}
									onLogout={handleLogout}
									onOpenCookiePreferences={handleCookiePreferences}
								/>
							</>
						}
					/>
					<AppSidebar
						ariaLabel={ariaLabel}
						items={items}
						linkComponent={Link}
						resizable={false}
						expandedWidth={SIDEBAR_EXPANDED_WIDTH}
						collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
					/>
					<div className="flex h-[calc(100dvh-3rem)]">
						<div className="w-(--app-sidebar-width) shrink-0 transition-[width] duration-150 ease-out" />
						<div className="min-w-0 flex-1 overflow-auto">{children}</div>
					</div>
				</div>
			</SidebarProvider>
		</TooltipProvider>
	);
};

export {Header};
