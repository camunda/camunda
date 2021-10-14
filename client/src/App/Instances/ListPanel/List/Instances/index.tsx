/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {Instance} from './Instance';
import {observer} from 'mobx-react';
import React from 'react';

const Instances = observer(
  React.forwardRef<HTMLTableSectionElement, {}>((props, ref) => {
    const {
      state: {processInstances},
    } = instancesStore;

    return (
      <tbody data-testid="instances-list" ref={ref}>
        {processInstances.map((instance) => (
          <Instance
            key={instance.id}
            instance={instance}
            isSelected={instanceSelectionStore.isInstanceChecked(instance.id)}
          />
        ))}
      </tbody>
    );
  })
);

export {Instances};
