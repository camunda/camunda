/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AnchorHTMLAttributes, forwardRef} from 'react';
import {Link, LinkProps} from 'react-router-dom';

type Props = Omit<LinkProps, 'to'> &
  AnchorHTMLAttributes<HTMLAnchorElement> & {
    to?: LinkProps['to'];
    href?: string;
  };

function isExternal(href: string): boolean {
  return /^[a-z][a-z\d+\-.]*:/i.test(href) || href.startsWith('//');
}

/**
 * Link renderer for the design system's `linkComponent` slot, which hands over
 * `to` for in-app routes and `href` for cross-app ones (e.g. the SaaS app switcher).
 * React Router v5 resolves every `to` against the basename, so an absolute URL has
 * to bypass the router and render a plain anchor.
 */
const ForwardRefLink = forwardRef<HTMLAnchorElement, Props>(({to, href, ...props}, ref) => {
  if (to === undefined && href !== undefined && isExternal(href)) {
    return <a {...props} href={href} ref={ref} />;
  }

  return <Link {...props} to={to ?? href ?? '/'} ref={ref} />;
});

ForwardRefLink.displayName = 'ForwardRefLink';

export default ForwardRefLink;
