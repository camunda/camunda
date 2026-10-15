/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
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
import type {CurrentApp} from '#/shared/c3/components/C3Provider';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getHubLink} from './getHubLink';

type Options = {
	currentApp: CurrentApp;
	webappLinks: Record<string, string>;
};

function getBreadcrumbIcon(key: string, currentApp: CamundaAppKey): NavIcon {
	if (key === 'org') {
		return OrganisationIcon;
	}
	if (key === 'cluster') {
		return ClusterIcon;
	}

	return camundaAppIcons[currentApp];
}

function getAppIcon(key: string): NavIcon | undefined {
	return Object.prototype.hasOwnProperty.call(camundaAppIcons, key) ? camundaAppIcons[key as CamundaAppKey] : undefined;
}

function getLinkProps(
	breadcrumb: BreadcrumbDescriptor,
	currentApp: CamundaAppKey,
	hubPaths: Record<string, string | undefined>,
): BreadcrumbDescriptor['linkProps'] {
	if (breadcrumb.key === 'app') {
		return {to: `/${currentApp}`};
	}

	const hubLink = hubPaths[breadcrumb.key];
	return hubLink === undefined ? breadcrumb.linkProps : {href: hubLink};
}

function mapBreadcrumb(
	breadcrumb: BreadcrumbDescriptor,
	currentApp: CamundaAppKey,
	hubPaths: Record<string, string | undefined>,
): NavBreadcrumbDescriptor {
	return {
		key: breadcrumb.key,
		label: breadcrumb.label,
		icon: getBreadcrumbIcon(breadcrumb.key, currentApp),
		onClick: breadcrumb.onClick,
		linkProps: getLinkProps(breadcrumb, currentApp, hubPaths),
		actions: breadcrumb.actions,
		dropdownTitle: breadcrumb.dropdownTitle,
		dropdownAriaLabel: breadcrumb.dropdownAriaLabel,
		trailingElement: breadcrumb.trailingElement,
		menuElement: breadcrumb.menuElement,
		dropdownItems: breadcrumb.dropdownItems?.map((item) => ({
			key: item.key,
			label: item.label,
			icon: breadcrumb.key === 'app' ? getAppIcon(item.key) : getBreadcrumbIcon(breadcrumb.key, currentApp),
			isSelected: item.isSelected,
			onClick: item.onClick,
			linkProps: item.linkProps,
			trailingElement: item.trailingElement,
		})),
	};
}

const Breadcrumbs: React.FC<Options> = ({currentApp, webappLinks}) => {
	const {t} = useTranslation();
	const activeItemKey = useRouterState({
		select: ({location}) => `${location.pathname}${location.searchStr}`,
	});
	const {organizationId, clusterId} = getBootConfig();
	const breadcrumbs = useClusterWebappBreadcrumbs({
		currentApp,
		webappLinks: organizationId !== null && clusterId !== null ? webappLinks : undefined,
	});
	const modelerLink = webappLinks['modeler'];
	const items = useMemo(() => {
		const hubPaths =
			organizationId !== null && clusterId !== null
				? {
						org: getHubLink(modelerLink, `/org/${organizationId}`),
						cluster: getHubLink(modelerLink, `/org/${organizationId}/cluster/${clusterId}`),
					}
				: {};
		return breadcrumbs.map((breadcrumb) => mapBreadcrumb(breadcrumb, currentApp, hubPaths));
	}, [breadcrumbs, currentApp, modelerLink, organizationId, clusterId]);

	if (items.length === 0) {
		return null;
	}

	return (
		<NavBreadcrumbSwitcher
			items={items}
			activeItemKey={activeItemKey}
			linkComponent={Link}
			aria-label={t('headerContextLabel')}
		/>
	);
};

export {Breadcrumbs};
