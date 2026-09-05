/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, type ComponentProps} from 'react';

import {IS_NAV_V2_ENABLED} from 'feature-flags';

import LegacyEntityList from './EntityList';

const EntityListV2 = lazy(() => import('./EntityListV2'));

export function EntityList(props: ComponentProps<typeof LegacyEntityList>) {
  if (!IS_NAV_V2_ENABLED) {
    return <LegacyEntityList {...props} />;
  }

  return (
    <Suspense fallback={null}>
      <EntityListV2 {...props} />
    </Suspense>
  );
}

export type {Action} from './EntityList';
