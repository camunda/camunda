/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Checkbox from 'modules/components/Checkbox';
import {Operations} from 'modules/components/Operations';
import StateIcon from 'modules/components/StateIcon';
import {getProcessName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';
import {
  TR,
  Cell,
  SelectionStatusIndicator,
  ProcessName,
  InstanceAnchor,
} from './styled';
import {Locations} from 'modules/routes';
import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {useNotifications} from 'modules/notifications';
import Table from 'modules/components/Table';
import React from 'react';

const {TD} = Table;

type Props = {
  instance: ProcessInstanceEntity;
  isSelected: boolean;
};

const Instance: React.FC<Props> = React.memo(({instance, isSelected}) => {
  const notifications = useNotifications();
  const {parentInstanceId} = instance;

  return (
    <TR key={instance.id} selected={isSelected}>
      <TD>
        <Cell>
          <SelectionStatusIndicator selected={isSelected} />
          <Checkbox
            data-testid="instance-checkbox"
            type="selection"
            isChecked={isSelected}
            onChange={() => instanceSelectionStore.selectInstance(instance.id)}
            title={`Select instance ${instance.id}`}
          />

          <StateIcon
            state={instance.state}
            data-testid={`${instance.state}-icon-${instance.id}`}
          />
          <ProcessName>{getProcessName(instance)}</ProcessName>
        </Cell>
      </TD>
      <TD>
        <InstanceAnchor
          to={(location) => Locations.instance(instance.id, location)}
          title={`View instance ${instance.id}`}
        >
          {instance.id}
        </InstanceAnchor>
      </TD>
      <TD>{`Version ${instance.processVersion}`}</TD>
      <TD data-testid="start-time">{formatDate(instance.startDate)}</TD>
      <TD data-testid="end-time">{formatDate(instance.endDate)}</TD>
      <TD data-testid="parent-process-id">
        {parentInstanceId !== null ? (
          <InstanceAnchor
            to={(location) => Locations.instance(parentInstanceId, location)}
            title={`View parent instance ${parentInstanceId}`}
          >
            {parentInstanceId}
          </InstanceAnchor>
        ) : (
          'None'
        )}
      </TD>
      <TD>
        <Operations
          instance={instance}
          selected={isSelected}
          onOperation={() =>
            instancesStore.markInstancesWithActiveOperations({
              ids: [instance.id],
            })
          }
          onFailure={() => {
            instancesStore.unmarkInstancesWithActiveOperations({
              instanceIds: [instance.id],
            });
            notifications.displayNotification('error', {
              headline: 'Operation could not be created',
            });
          }}
        />
      </TD>
    </TR>
  );
});

export {Instance};
