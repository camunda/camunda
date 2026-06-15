/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { forwardRef, type ComponentPropsWithoutRef } from "react";
import { Link, type LinkProps } from "react-router-dom";

type SmartLinkProps = Partial<LinkProps> &
  Omit<ComponentPropsWithoutRef<"a">, keyof LinkProps>;

/**
 * Adapter for C3NavigationV2's `linkComponent` slot until upstream c3 fixes
 * the breadcrumb-bar to render href-only items as plain `<a>` (see
 * camunda-cloud-management-apps PR #8849). C3's `useClusterWebappBreadcrumbs`
 * emits `linkProps: {href}` for cross-cluster / cross-webapp URLs, which
 * react-router's `Link` consumes incorrectly (preventDefaults click, then
 * tries to navigate to undefined `to`). Render plain `<a>` when `href` is
 * present and route via `Link` only when `to` is set.
 */
export const SmartLink = forwardRef<HTMLAnchorElement, SmartLinkProps>(
  ({ to, href, ...rest }, ref) => {
    if (href !== undefined) {
      return <a ref={ref} href={href} {...rest} />;
    }
    return <Link ref={ref} to={to ?? ""} {...rest} />;
  },
);
SmartLink.displayName = "SmartLink";
