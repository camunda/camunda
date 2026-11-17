/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Skeleton as SkeletonBlock } from "@camunda/design-system";
import { Cell, HeadCell } from "./components";

type SkeletonProps = {
  entries?: number;
};

const Skeleton: FC<SkeletonProps> = ({ entries = 2 }) => (
  <dl className="m-0 flex flex-col">
    {new Array(entries).fill(undefined).map((_, i) => (
      <div
        key={`list-skeleton-row-${i}`}
        className="flex gap-4 py-3 shadow-[inset_0_-1px_0_var(--border)]"
      >
        <HeadCell>
          <SkeletonBlock className="h-3 w-48" />
        </HeadCell>
        <Cell>
          <SkeletonBlock className="h-3 w-full" />
        </Cell>
      </div>
    ))}
  </dl>
);

export default Skeleton;
