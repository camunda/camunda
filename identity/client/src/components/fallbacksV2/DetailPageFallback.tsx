/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, Fragment, ReactNode } from "react";
import { Skeleton } from "@camunda/design-system";
import { StackPage } from "src/components/layoutV2/Page";

const TAB_COUNT = 4;
const BREADCRUMB_ITEM_COUNT = 3;

type DetailPageHeaderFallbackProps = { hasOverflowMenu?: boolean };

export const DetailPageHeaderFallback: FC<DetailPageHeaderFallbackProps> = ({
  hasOverflowMenu = true,
}) => {
  return (
    <div className="flex items-center gap-3">
      <Skeleton className="mt-2 h-5 w-40" />
      {hasOverflowMenu && <Skeleton className="size-4 rounded-full" />}
    </div>
  );
};

type DetailPageFallbackProps = {
  children?: ReactNode;
  hasBreadcrumb?: boolean;
};

const DetailPageFallback: FC<DetailPageFallbackProps> = ({
  children,
  hasBreadcrumb = false,
}) => (
  <StackPage>
    <>
      {hasBreadcrumb && (
        <div className="flex items-center gap-2">
          {Array.from({ length: BREADCRUMB_ITEM_COUNT }).map((_, i) => (
            <Fragment key={i}>
              <Skeleton className="h-4 w-16" />
              {i < BREADCRUMB_ITEM_COUNT - 1 && (
                <span aria-hidden className="text-neutral-foreground-subtle/40">
                  /
                </span>
              )}
            </Fragment>
          ))}
        </div>
      )}
      <DetailPageHeaderFallback />
      <section>
        <div className="flex h-10 items-center gap-2 border-b border-border px-1">
          {Array.from({ length: TAB_COUNT }).map((_, i) => (
            <Skeleton key={i} className="h-5 w-20" />
          ))}
        </div>
        <div className="p-2">{children}</div>
      </section>
    </>
  </StackPage>
);

export default DetailPageFallback;
