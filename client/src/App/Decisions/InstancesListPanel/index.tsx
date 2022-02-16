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
import {InstancesMessage} from 'modules/components/InstancesMessage';
import {Container, Name, State} from './styled';

const InstancesListPanel: React.FC = observer(() => {
  const {
    state: {status, decisionInstances},
    areDecisionInstancesEmpty,
  } = decisionInstancesStore;

  useEffect(() => {
    decisionInstancesStore.fetchInstances();
  }, []);

  const shouldDisplaySkeleton = ['initial', 'first-fetch'].includes(status);

  return (
    <Container overflow={shouldDisplaySkeleton ? 'hidden' : 'auto'}>
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
        {shouldDisplaySkeleton && <Skeleton />}
        {status === 'error' && <InstancesMessage type="error" />}
        {areDecisionInstancesEmpty && <InstancesMessage type="empty" />}
        <Table.TBody>
          {decisionInstances.map((instance) => {
            return (
              <Table.TR key={instance.id}>
                <Name>
                  {instance.state === 'COMPLETED' && (
                    <State
                      icon="state:completed"
                      color="medLight"
                      data-testid={`completed-icon-${instance.id}`}
                    />
                  )}
                  {instance.state === 'FAILED' && (
                    <State
                      icon="state:incident"
                      color="danger"
                      data-testid={`failed-icon-${instance.id}`}
                    />
                  )}
                  {instance.name}
                </Name>
                <Table.TD>{instance.id}</Table.TD>
                <Table.TD>{instance.version}</Table.TD>
                <Table.TD>{instance.evaluationTime}</Table.TD>
                <Table.TD>{instance.processInstanceId}</Table.TD>
              </Table.TR>
            );
          })}
        </Table.TBody>
      </Table>
    </Container>
  );
});

export {InstancesListPanel};
