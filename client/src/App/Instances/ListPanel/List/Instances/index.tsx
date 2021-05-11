/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Table from 'modules/components/Table';
import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {Instance} from './Instance';
import {observer} from 'mobx-react';

const {TBody} = Table;

const Instances: React.FC = observer(() => {
  const {
    state: {processInstances},
  } = instancesStore;

  return (
    <TBody data-testid="instances-list">
      {processInstances.map((instance) => (
        <Instance
          key={instance.id}
          instance={instance}
          isSelected={instanceSelectionStore.isInstanceChecked(instance.id)}
        />
      ))}
    </TBody>
  );
});

export {Instances};
