/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {C4Provider, type ToolDescriptor} from '@camunda/design-system';
import {InfoButton, InfoPanel} from './InfoTool';

function useInfoTool(isPaidPlan: boolean): ToolDescriptor[] {
  return useMemo(
    () => [
      {
        key: 'info',
        label: 'Info',
        renderButton: InfoButton,
        panel: (
          <C4Provider>
            <InfoPanel isPaidPlan={isPaidPlan} />
          </C4Provider>
        ),
      },
    ],
    [isPaidPlan],
  );
}

export {useInfoTool};
