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
	NavBreadcrumbSwitcher,
	SidebarProvider,
	toast,
	TooltipProvider,
	type NavBreadcrumbDescriptor,
} from '@camunda/design-system';
import {useSuspenseQuery} from '@tanstack/react-query';
import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';
import {useSidebarNavigation} from '#/shared/header/useSidebarNavigation.shadcn';
import {AccountMenu} from './AccountMenu';
import {HelpMenu} from './HelpMenu';
import {LicenseBadges} from './LicenseBadges';
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
	const breadcrumbItems = useMemo<NavBreadcrumbDescriptor[]>(
		() =>
			product === undefined
				? []
				: [
						{
							key: 'app',
							label: product.label,
							icon: product.icon,
							linkProps: {to: homeRoute},
						},
					],
		[homeRoute, product],
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
						breadcrumb={
							breadcrumbItems.length === 0 ? undefined : (
								<NavBreadcrumbSwitcher
									items={breadcrumbItems}
									linkComponent={Link}
									aria-label={t('headerContextLabel')}
								/>
							)
						}
						trailing={<LicenseBadges license={license} />}
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
