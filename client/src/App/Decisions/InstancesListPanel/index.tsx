/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {useEffect} from 'react';
import {Skeleton} from './Skeleton';
import Table from 'modules/components/Table';

const InstancesListPanel: React.FC = observer(() => {
  const {
    state: {status, decisionInstances},
    areDecisionInstancesEmpty,
  } = decisionInstancesStore;

  useEffect(() => {
    decisionInstancesStore.fetchInstances();
  }, []);

  return (
    <div>
      <div>Instances</div>
      <Table>
        <Table.THead>
          <Table.TR>
            <Table.TH>Decision</Table.TH>
            <Table.TH>Decision Instance Id</Table.TH>
            <Table.TH>Version</Table.TH>
            <Table.TH>Evaluation Time</Table.TH>
            <Table.TH>Process Instance Id</Table.TH>
          </Table.TR>
        </Table.THead>
        {['initial', 'first-fetch'].includes(status) && <Skeleton />}
        {status === 'error' && (
          <Table.TBody>
            <Table.TR>
              <Table.TD>Instances could not be fetched</Table.TD>
            </Table.TR>
          </Table.TBody>
        )}
        {areDecisionInstancesEmpty && (
          <Table.TBody>
            <Table.TR>
              <Table.TD>
                There are no Instances matching this filter set
              </Table.TD>
            </Table.TR>
          </Table.TBody>
        )}
        <Table.TBody>
          {decisionInstances.map((instance) => {
            return (
              <Table.TR key={instance.id}>
                <Table.TD>{instance.name}</Table.TD>
                <Table.TD>{instance.id}</Table.TD>
                <Table.TD>{instance.version}</Table.TD>
                <Table.TD>{instance.evaluationTime}</Table.TD>
                <Table.TD>{instance.processInstanceId}</Table.TD>
              </Table.TR>
            );
          })}
        </Table.TBody>
      </Table>
    </div>
  );
});

export {InstancesListPanel};
