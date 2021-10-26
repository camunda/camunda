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
import {TR, Cell, SelectionStatusIndicator, ProcessName} from './styled';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';
import {instancesStore} from 'modules/stores/instances';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {useNotifications} from 'modules/notifications';
import Table from 'modules/components/Table';
import React from 'react';
import {Restricted} from 'modules/components/Restricted';

const {TD} = Table;

type Props = {
  instance: ProcessInstanceEntity;
  isSelected: boolean;
};

const Instance: React.FC<Props> = React.memo(({instance, isSelected}) => {
  const notifications = useNotifications();
  const {parentInstanceId} = instance;

  return (
    <TR
      key={instance.id}
      selected={isSelected}
      aria-label={`Instance ${instance.id}`}
    >
      <TD>
        <Cell>
          <Restricted scopes={['write']}>
            <>
              <SelectionStatusIndicator selected={isSelected} />
              <Checkbox
                data-testid="instance-checkbox"
                type="selection"
                isChecked={isSelected}
                onChange={() =>
                  instanceSelectionStore.selectInstance(instance.id)
                }
                title={`Select instance ${instance.id}`}
              />
            </>
          </Restricted>
          <StateIcon
            state={instance.state}
            data-testid={`${instance.state}-icon-${instance.id}`}
          />
          <ProcessName>{getProcessName(instance)}</ProcessName>
        </Cell>
      </TD>
      <TD>
        <Link
          to={(location) => Locations.instance(instance.id, location)}
          title={`View instance ${instance.id}`}
        >
          {instance.id}
        </Link>
      </TD>
      <TD>{`Version ${instance.processVersion}`}</TD>
      <TD data-testid="start-time">{formatDate(instance.startDate)}</TD>
      <TD data-testid="end-time">{formatDate(instance.endDate)}</TD>
      <TD data-testid="parent-process-id">
        {parentInstanceId !== null ? (
          <Link
            to={(location) => Locations.instance(parentInstanceId, location)}
            title={`View parent instance ${parentInstanceId}`}
          >
            {parentInstanceId}
          </Link>
        ) : (
          'None'
        )}
      </TD>
      <Restricted scopes={['write']}>
        <TD>
          <Operations
            instance={instance}
            isSelected={isSelected}
            onOperation={(operationType: OperationEntityType) =>
              instancesStore.markInstancesWithActiveOperations({
                ids: [instance.id],
                operationType,
              })
            }
            onError={(operationType: OperationEntityType) => {
              instancesStore.unmarkInstancesWithActiveOperations({
                instanceIds: [instance.id],
                operationType,
              });
              notifications.displayNotification('error', {
                headline: 'Operation could not be created',
              });
            }}
          />
        </TD>
      </Restricted>
    </TR>
  );
});

export {Instance};
