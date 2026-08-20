/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps} from 'react';
import {Loading as CarbonLoading} from '@carbon/react';

import './Loading.scss';

export function Loading(props: Omit<ComponentProps<typeof CarbonLoading>, 'withOverlay'>) {
  return (
    <div className="Loading">
      <CarbonLoading withOverlay={false} {...props} />
    </div>
  );
}
