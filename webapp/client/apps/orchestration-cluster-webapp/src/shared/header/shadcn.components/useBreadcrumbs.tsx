/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, type ReactNode} from 'react';
import {useRouterState, Link} from '@tanstack/react-router';
import {
	preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
	type BreadcrumbDescriptor,
} from '@camunda/camunda-composite-components';
import {
	ClusterIcon,
	NavBreadcrumbSwitcher,
	OrganisationIcon,
	camundaAppIcons,
	type CamundaAppKey,
	type NavBreadcrumbDescriptor,
	type NavIcon,
} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {getBootConfig} from '#/shared/config/getBootConfig';

type Options = {
	webappLinks: Record<string, string>;
};

function getBreadcrumbIcon(key: string): NavIcon {
	if (key === 'org') {
		return OrganisationIcon;
	}
	if (key === 'cluster') {
		return ClusterIcon;
	}

	return camundaAppIcons.tasklist;
}

function getAppIcon(key: string): NavIcon | undefined {
	return Object.prototype.hasOwnProperty.call(camundaAppIcons, key) ? camundaAppIcons[key as CamundaAppKey] : undefined;
}

function mapBreadcrumb(breadcrumb: BreadcrumbDescriptor): NavBreadcrumbDescriptor {
	return {
		key: breadcrumb.key,
		label: breadcrumb.label,
		icon: getBreadcrumbIcon(breadcrumb.key),
		onClick: breadcrumb.onClick,
		linkProps: breadcrumb.key === 'app' ? {to: '/tasklist'} : breadcrumb.linkProps,
		actions: breadcrumb.actions,
		dropdownTitle: breadcrumb.dropdownTitle,
		dropdownAriaLabel: breadcrumb.dropdownAriaLabel,
		dropdownItems: breadcrumb.dropdownItems?.map((item) => ({
			key: item.key,
			label: item.label,
			icon: breadcrumb.key === 'app' ? getAppIcon(item.key) : getBreadcrumbIcon(breadcrumb.key),
			isSelected: item.isSelected,
			onClick: item.onClick,
			linkProps: item.linkProps,
		})),
	};
}

function useBreadcrumbs({webappLinks}: Options): ReactNode | undefined {
	const {t} = useTranslation();
	const activeItemKey = useRouterState({
		select: ({location}) => `${location.pathname}${location.searchStr}`,
	});
	const {organizationId, clusterId} = getBootConfig();
	const breadcrumbs = useClusterWebappBreadcrumbs({
		currentApp: 'tasklist',
		webappLinks: organizationId !== null && clusterId !== null ? webappLinks : undefined,
	});
	const items = useMemo(() => breadcrumbs.map(mapBreadcrumb), [breadcrumbs]);

	if (items.length === 0) {
		return undefined;
	}

	return (
		<NavBreadcrumbSwitcher
			items={items}
			activeItemKey={activeItemKey}
			linkComponent={Link}
			aria-label={t('headerContextLabel')}
		/>
	);
}

export {useBreadcrumbs};
