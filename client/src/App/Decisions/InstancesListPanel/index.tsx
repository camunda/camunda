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
import {Header} from './Header';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';

const InstancesListPanel: React.FC = observer(() => {
  const {
    state: {status, decisionInstances, filteredInstancesCount},
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
      <Header instancesCount={filteredInstancesCount} />
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
              {decisionInstances.map(
                ({
                  id,
                  state,
                  name,
                  version,
                  evaluationTime,
                  processInstanceId,
                }) => {
                  return (
                    <TR key={id}>
                      <Name>
                        <State
                          state={state}
                          data-testid={`${state}-icon-${id}`}
                        />
                        {name}
                      </Name>
                      <TD>
                        <Link
                          to={(location) =>
                            Locations.decisionInstance(id, location)
                          }
                          title={`View decision instance ${id}`}
                        >
                          {id}
                        </Link>
                      </TD>
                      <TD>{version}</TD>
                      <TD>{formatDate(evaluationTime)}</TD>
                      <TD>
                        {processInstanceId !== null ? (
                          <Link
                            to={(location) =>
                              Locations.instance(processInstanceId, location)
                            }
                            title={`View process instance ${processInstanceId}`}
                          >
                            {processInstanceId}
                          </Link>
                        ) : (
                          'None'
                        )}
                      </TD>
                    </TR>
                  );
                }
              )}
            </Table.TBody>
          </Table>
        </ScrollableContent>
      </List>
    </Container>
  );
});

export {InstancesListPanel};
