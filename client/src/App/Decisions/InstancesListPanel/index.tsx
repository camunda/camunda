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
import {
  Container,
  Name,
  State,
  Title,
  DecisionColumnHeader,
  TH,
  TD,
  TR,
} from './styled';
import {formatDate} from 'modules/utils/date';

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
      <Title>Instances</Title>
      <Table>
        <Table.THead>
          <TR>
            <TH>
              <DecisionColumnHeader>Decision</DecisionColumnHeader>
            </TH>
            <TH>Decision Instance Id</TH>
            <TH>Version</TH>
            <TH>Evaluation Time</TH>
            <TH>Process Instance Id</TH>
          </TR>
        </Table.THead>
        {shouldDisplaySkeleton && <Skeleton />}
        {status === 'error' && <InstancesMessage type="error" />}
        {areDecisionInstancesEmpty && <InstancesMessage type="empty" />}
        <Table.TBody>
          {decisionInstances.map((instance) => {
            return (
              <TR key={instance.id}>
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
                <TD>{instance.id}</TD>
                <TD>{instance.version}</TD>
                <TD>{formatDate(instance.evaluationTime)}</TD>
                <TD>{instance.processInstanceId}</TD>
              </TR>
            );
          })}
        </Table.TBody>
      </Table>
    </Container>
  );
});

export {InstancesListPanel};
