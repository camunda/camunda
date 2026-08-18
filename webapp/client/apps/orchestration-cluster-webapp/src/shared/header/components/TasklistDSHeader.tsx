/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Building, Briefcase} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {AppHeader, NavBreadcrumb, NavBreadcrumbItem, useMediaQuery} from '@camunda/design-system';
import type {CurrentUser, License} from '@camunda/camunda-api-zod-schemas/8.10';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {notify} from '#/shared/notifications/toast';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {LicenseBadges} from './LicenseBadges';
import {HelpMenu} from './HelpMenu';
import {AccountMenu} from './AccountMenu';

type Props = {
	currentUser: CurrentUser;
	license: License;
};

// DS-only header for Tasklist routes. Carries global navigation (organization,
// workspace) rather than the Tasks/Processes tabs the Carbon header showed —
// that app-level navigation now lives in the sidebar rail (TasklistNavLayout),
// which is where it belongs once this app sits inside Hub. Operate and Admin
// keep the Carbon header for now (see Header.tsx); lifting this pattern to
// them is a separate follow-up.
const TasklistDSHeader: React.FC<Props> = ({currentUser, license}) => {
	const {t} = useTranslation();
	const {canLogout} = getClientConfig().authentication;
	const {displayName} = currentUser;
	// Below `sm` (--breakpoint-sm, 40rem/640px) — narrower than the `md`
	// breakpoint that flips the sidebar into its mobile overlay — there isn't
	// room for the full org/workspace chain or the license tags: only the
	// current (last) breadcrumb segment stays, and the tags drop entirely.
	const isLowMobile = useMediaQuery('(width < 40rem)');

	const logoutWithNotification = () => {
		notify({
			kind: 'info',
			title: t('notificationLogOutTitle'),
			subtitle: t('notificationLogOutSubtitle'),
			isDismissable: true,
		});
		setTimeout(authenticationStore.handleLogout, 1000);
	};

	return (
		<AppHeader
			skipToContentTargetId="main-content"
			breadcrumb={
				<NavBreadcrumb aria-label={t('tasklist.headerWorkspaceNavAria')}>
					{/* TODO(hub): placeholders until Tasklist runs inside Hub, where
					    organization/workspace context — and something to switch
					    between — actually exists. See docs/migration/human-follow-up.md. */}
					{!isLowMobile && (
						<NavBreadcrumbItem icon={<Building aria-hidden />} label={t('tasklist.headerOrganizationPlaceholder')} />
					)}
					<NavBreadcrumbItem icon={<Briefcase aria-hidden />} label={t('tasklist.headerWorkspacePlaceholder')} />
				</NavBreadcrumb>
			}
			trailing={isLowMobile ? undefined : <LicenseBadges license={license} />}
			actions={
				<>
					<HelpMenu isPaidPlan={['paid-cc', 'enterprise'].includes(currentUser.salesPlanType ?? '')} />
					<AccountMenu
						displayName={displayName}
						canLogout={canLogout}
						onLogout={logoutWithNotification}
						onOpenCookiePreferences={
							window.Osano?.cm === undefined
								? undefined
								: () => {
										window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
									}
						}
					/>
				</>
			}
		/>
	);
};

export {TasklistDSHeader};
