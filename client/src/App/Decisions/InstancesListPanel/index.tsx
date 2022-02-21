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
  List,
  ScrollableContent,
  THead,
  TRHeader,
} from './styled';
import {formatDate} from 'modules/utils/date';
import {ColumnHeader} from 'modules/components/Table/ColumnHeader';
import {useLocation} from 'react-router-dom';

const InstancesListPanel: React.FC = observer(() => {
  const {
    state: {status, decisionInstances},
    areDecisionInstancesEmpty,
  } = decisionInstancesStore;
  const location = useLocation();

  useEffect(() => {
    decisionInstancesStore.fetchInstancesFromFilters();
  }, [location.search]);

  const shouldDisplaySkeleton = ['initial', 'first-fetch'].includes(status);

  const isSortingDisabled =
    areDecisionInstancesEmpty ||
    ['initial', 'first-fetch', 'fetching'].includes(status);

  return (
    <Container>
      <Title>Instances</Title>
      <List>
        <ScrollableContent overflow={shouldDisplaySkeleton ? 'hidden' : 'auto'}>
          <Table>
            <THead>
              <TRHeader>
                <TH>
                  <DecisionColumnHeader>
                    <ColumnHeader
                      disabled={isSortingDisabled}
                      label="Decision"
                      sortKey="decision"
                    />
                  </DecisionColumnHeader>
                </TH>
                <TH>
                  <ColumnHeader
                    disabled={isSortingDisabled}
                    label="Decision Instance Id"
                    sortKey="decisionInstanceId"
                  />
                </TH>
                <TH>
                  <ColumnHeader
                    disabled={isSortingDisabled}
                    label="Version"
                    sortKey="version"
                  />
                </TH>
                <TH>
                  <ColumnHeader
                    disabled={isSortingDisabled}
                    label="Evaluation Time"
                    sortKey="evaluationTime"
                    isDefault
                  />
                </TH>
                <TH>
                  <ColumnHeader
                    disabled={isSortingDisabled}
                    label="Process Instance Id"
                    sortKey="processInstanceId"
                  />
                </TH>
              </TRHeader>
            </THead>
            {shouldDisplaySkeleton && <Skeleton />}
            {status === 'error' && <InstancesMessage type="error" />}
            {areDecisionInstancesEmpty && <InstancesMessage type="empty" />}
            <Table.TBody>
              {decisionInstances.map((instance) => {
                return (
                  <TR key={instance.id}>
                    <Name>
                      <State
                        state={instance.state}
                        data-testid={`${instance.state}-icon-${instance.id}`}
                      />
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
        </ScrollableContent>
      </List>
    </Container>
  );
});

export {InstancesListPanel};
