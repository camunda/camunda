/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useLocation} from 'react-router-dom';
import {
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
  type BreadcrumbDescriptor,
  useC3Profile,
} from '@camunda/camunda-composite-components';
import {
  ClusterIcon,
  NavBreadcrumbSwitcher,
  OrganisationIcon,
  camundaAppIcons,
  type NavBreadcrumbDescriptor,
} from '@camunda/design-system';
import {ForwardRefLink} from './ForwardRefLink';
import {tracking} from 'modules/tracking';
import {Paths} from 'modules/Routes';

const APP_SWITCHER_ORDER = [
  'console',
  'modeler',
  'tasklist',
  'operate',
  'optimize',
  'admin',
  'identity',
];

type BreadcrumbOptions = {
  isSaas: boolean;
  currentClusterUuid?: string | null;
  webappLinks?: Record<string, string>;
};

function getAppIcon(key: string) {
  switch (key) {
    case 'operate':
      return camundaAppIcons.operate;
    case 'tasklist':
      return camundaAppIcons.tasklist;
    case 'optimize':
      return camundaAppIcons.optimize;
    case 'admin':
      return camundaAppIcons.admin;
    case 'identity':
      return camundaAppIcons.identity;
    case 'modeler':
      return camundaAppIcons.modeler;
    case 'console':
      return camundaAppIcons.console;
    default:
      return undefined;
  }
}

function buildConsoleUrl(modelerUrl: string, organizationId: string) {
  try {
    const url = new URL(modelerUrl);
    url.hostname = url.hostname.replace(/^modeler\./, 'console.');
    url.pathname = `/org/${organizationId}`;
    url.search = '';
    return url.toString();
  } catch {
    return undefined;
  }
}

function augmentAppSwitcher(
  breadcrumbs: BreadcrumbDescriptor[],
  {
    activeOrg,
    clusters,
    currentClusterUuid,
    isSaas,
    webappLinks,
  }: BreadcrumbOptions &
    Pick<ReturnType<typeof useC3Profile>, 'activeOrg' | 'clusters'>,
) {
  if (!isSaas) {
    return breadcrumbs;
  }

  const currentCluster = clusters?.find(
    ({uuid}) => uuid === currentClusterUuid,
  );
  const appBreadcrumb = breadcrumbs.find(({key}) => key === 'app');
  if (appBreadcrumb === undefined) {
    return breadcrumbs;
  }

  const dropdownItems = [
    ...(appBreadcrumb.dropdownItems ?? [
      {
        key: 'operate',
        label: 'Operate',
        isSelected: true,
      },
    ]),
  ];
  const existingKeys = new Set(dropdownItems.map(({key}) => key));
  const organizationUrl = breadcrumbs.find(({key}) => key === 'org')?.linkProps
    ?.href;
  const modelerUrl =
    webappLinks?.['modeler'] ?? currentCluster?.endpoints.modeler;
  const consoleUrl =
    webappLinks?.['console'] ??
    currentCluster?.endpoints.console ??
    organizationUrl ??
    (modelerUrl !== undefined && activeOrg !== null
      ? buildConsoleUrl(modelerUrl, activeOrg.uuid)
      : undefined);

  if (consoleUrl !== undefined && !existingKeys.has('console')) {
    dropdownItems.push({
      key: 'console',
      label: 'Console',
      linkProps: {href: consoleUrl},
    });
    existingKeys.add('console');
  }

  const canOpenModeler = activeOrg?.permissions?.org?.webide?.read === true;
  if (
    canOpenModeler &&
    modelerUrl !== undefined &&
    !existingKeys.has('modeler')
  ) {
    dropdownItems.push({
      key: 'modeler',
      label: 'Modeler',
      linkProps: {href: modelerUrl},
    });
    existingKeys.add('modeler');
  }

  if (activeOrg !== null) {
    for (const app of ['tasklist', 'optimize'] as const) {
      const directUrl = webappLinks?.[app];
      const hasClusterEndpoint =
        clusters?.some(({endpoints}) => endpoints[app] !== undefined) ?? false;
      const canReadApp = activeOrg.permissions?.cluster?.[app]?.read === true;

      if (!existingKeys.has(app) && canReadApp && directUrl !== undefined) {
        dropdownItems.push({
          key: app,
          label: app === 'tasklist' ? 'Tasklist' : 'Optimize',
          linkProps: {href: directUrl},
        });
        existingKeys.add(app);
      } else if (!existingKeys.has(app) && canReadApp && !hasClusterEndpoint) {
        dropdownItems.push({
          key: app,
          label: app === 'tasklist' ? 'Tasklist' : 'Optimize',
          linkProps: {
            href: new URL(
              `/org/${activeOrg.uuid}/appTeaser/${app}`,
              window.location.origin,
            ).toString(),
          },
        });
        existingKeys.add(app);
      }
    }
  }

  dropdownItems.sort(
    (left, right) =>
      APP_SWITCHER_ORDER.indexOf(left.key) -
      APP_SWITCHER_ORDER.indexOf(right.key),
  );

  return breadcrumbs.map((breadcrumb) =>
    breadcrumb.key === 'app'
      ? {
          ...breadcrumb,
          dropdownTitle: 'Switch application',
          dropdownAriaLabel: 'Switch application',
          dropdownItems,
        }
      : breadcrumb,
  );
}

function mapBreadcrumb(
  breadcrumb: BreadcrumbDescriptor,
): NavBreadcrumbDescriptor {
  const icon =
    breadcrumb.key === 'org'
      ? OrganisationIcon
      : breadcrumb.key === 'cluster'
        ? ClusterIcon
        : camundaAppIcons.operate;

  return {
    key: breadcrumb.key,
    label: breadcrumb.label,
    icon,
    onClick: breadcrumb.onClick,
    linkProps:
      breadcrumb.key === 'app'
        ? (breadcrumb.linkProps ?? {to: Paths.dashboard()})
        : breadcrumb.linkProps,
    actions: breadcrumb.actions,
    dropdownTitle: breadcrumb.dropdownTitle,
    dropdownAriaLabel: breadcrumb.dropdownAriaLabel,
    dropdownItems: breadcrumb.dropdownItems?.map((item) => ({
      key: item.key,
      label: item.label,
      icon:
        breadcrumb.key === 'org'
          ? OrganisationIcon
          : breadcrumb.key === 'cluster'
            ? ClusterIcon
            : getAppIcon(item.key),
      isSelected: item.isSelected,
      onClick:
        breadcrumb.key === 'app'
          ? () => {
              tracking.track({
                eventName: 'app-switcher-item-clicked',
                app: item.key,
              });
              item.onClick?.();
            }
          : item.onClick,
      linkProps: item.linkProps,
      trailingElement: item.trailingElement,
    })),
    trailingElement: breadcrumb.trailingElement,
    menuElement: breadcrumb.menuElement,
  };
}

function useBreadcrumbs(options: BreadcrumbOptions) {
  const location = useLocation();
  const profile = useC3Profile();
  const breadcrumbs = useClusterWebappBreadcrumbs({
    currentApp: 'operate',
    webappLinks: options.isSaas ? options.webappLinks : undefined,
  });
  const items = useMemo(
    () =>
      augmentAppSwitcher(breadcrumbs, {
        ...options,
        activeOrg: profile.activeOrg,
        clusters: profile.clusters,
      }).map(mapBreadcrumb),
    [breadcrumbs, options, profile.activeOrg, profile.clusters],
  );

  return (
    <NavBreadcrumbSwitcher
      items={items}
      activeItemKey={`${location.pathname}${location.search}`}
      linkComponent={ForwardRefLink}
      aria-label="Camunda context"
    />
  );
}

export {useBreadcrumbs};
