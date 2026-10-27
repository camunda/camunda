/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";

type CellProps = { children: ReactNode };

export const Cell: FC<CellProps> = ({ children }) => (
  <dd className="m-0 flex-1">{children}</dd>
);

export const HeadCell: FC<CellProps> = ({ children }) => (
  <dt className="w-64 shrink-0 whitespace-nowrap font-medium text-neutral-foreground-strong">
    {children}
  </dt>
);
