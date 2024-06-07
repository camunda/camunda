/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {Loading as CarbonLoading} from '@carbon/react';

import './Loading.scss';

interface LoadingProps extends Omit<ComponentProps<typeof CarbonLoading>, 'withOverlay'> {}

export function Loading(props: LoadingProps) {
  return (
    <div className="Loading">
      <CarbonLoading withOverlay={false} {...props} />
    </div>
  );
}
