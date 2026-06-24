/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, lazy, ReactNode, Suspense } from "react";
import { ListPageFallback } from "src/components/fallbacks";

type LazyProps = {
  load: Parameters<typeof lazy>[0];
  fallback?: ReactNode;
  elementProps?: Record<string, unknown>;
};

const Lazy: FC<LazyProps> = ({
  fallback = <ListPageFallback />,
  load,
  elementProps,
}) => {
  const Element = lazy(load);

  return (
    <Suspense fallback={fallback}>
      <Element {...elementProps} />
    </Suspense>
  );
};

export default Lazy;
