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
} from '@camunda/camunda-composite-components';
import {
  NavBreadcrumbSwitcher,
  type NavBreadcrumbDescriptor,
} from '@camunda/design-system';
import {ForwardRefLink} from './ForwardRefLink';
import {tracking} from 'modules/tracking';
import {Paths} from 'modules/Routes';

type BreadcrumbOptions = {
  isSaas: boolean;
  webappLinks?: Record<string, string>;
};

function mapBreadcrumb(
  breadcrumb: BreadcrumbDescriptor,
): NavBreadcrumbDescriptor {
  return {
    key: breadcrumb.key,
    label: breadcrumb.label,
    icon: breadcrumb.icon,
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
      icon: item.icon,
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
  const breadcrumbs = useClusterWebappBreadcrumbs({
    currentApp: 'operate',
    webappLinks: options.isSaas ? options.webappLinks : undefined,
  });
  const items = useMemo(() => breadcrumbs.map(mapBreadcrumb), [breadcrumbs]);

  return (
    <NavBreadcrumbSwitcher
      items={items}
      activeItemKey={`${location.pathname}${location.search}`}
      linkComponent={ForwardRefLink}
      aria-label="Camunda context"
      className="operate-nav-v2-breadcrumb"
    />
  );
}

export {useBreadcrumbs};
