/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {observer} from 'mobx-react';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';

const InstancesByProcess: React.FC = observer(() => {
  const {
    state: {processInstances, status},
    hasNoInstances,
  } = processInstancesByNameStore;

  if (['initial', 'first-fetch'].includes(status)) {
    return <div>skeleton</div>;
  }

  if (hasNoInstances) {
    return <div>empty state</div>;
  }

  if (status === 'error') {
    return <div>error state</div>;
  }

  return (
    <PartiallyExpandableDataTable
      headers={[{key: 'instance', header: 'instance'}]}
      rows={processInstances.map(({bpmnProcessId}) => {
        return {
          id: bpmnProcessId,
          instance: bpmnProcessId,
        };
      })}
      expandedContents={processInstances.reduce(
        (accumulator, {bpmnProcessId, processName, processes}) => {
          if (processes.length <= 1) {
            return accumulator;
          }

          return {
            ...accumulator,
            [bpmnProcessId]: <div>{processName || bpmnProcessId}</div>,
          };
        },
        {},
      )}
    />
  );
});
export {InstancesByProcess};
