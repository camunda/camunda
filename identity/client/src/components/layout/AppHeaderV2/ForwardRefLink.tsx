/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { forwardRef } from "react";
import { Link, type LinkProps } from "react-router-dom";

type Props = Omit<LinkProps, "to"> & {
  to?: LinkProps["to"];
  href?: string;
};

const ForwardRefLink = forwardRef<HTMLAnchorElement, Props>(
  ({ to, href, ...props }, ref) => (
    <Link {...props} to={to ?? href ?? "/"} ref={ref} />
  ),
);

ForwardRefLink.displayName = "ForwardRefLink";

export { ForwardRefLink };
