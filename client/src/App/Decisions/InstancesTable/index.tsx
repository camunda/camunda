/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {autorun} from 'mobx';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {useEffect, useRef} from 'react';
import {Skeleton} from './Skeleton';
import Table from 'modules/components/Table';
import {InstancesMessage} from 'modules/components/InstancesMessage';
import {
  Container,
  DecisionColumnHeader,
  TH,
  List,
  ScrollableContent,
  THead,
  TRHeader,
  Spinner,
} from './styled';
import {ColumnHeader} from 'modules/components/Table/ColumnHeader';
import {useLocation} from 'react-router-dom';
import {Header} from './Header';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {DecisionInstances} from './DecisionInstances';

const ROW_HEIGHT = 37;

const InstancesTable: React.FC = observer(() => {
  const {
    state: {status, filteredInstancesCount, latestFetch},
    areDecisionInstancesEmpty,
    hasLatestDecisionInstances,
  } = decisionInstancesStore;
  const location = useLocation();

  let scrollableContentRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    decisionInstancesStore.fetchInstancesFromFilters();
  }, [location.search]);

  const shouldDisplaySkeleton = ['initial', 'first-fetch'].includes(status);

  const isSortingDisabled =
    areDecisionInstancesEmpty ||
    ['initial', 'first-fetch', 'fetching', 'error'].includes(status);

  useEffect(() => {
    let disposer = autorun(() => {
      if (decisionInstancesStore.state.status === 'fetching') {
        scrollableContentRef?.current?.scrollTo?.(0, 0);
      }
    });

    return () => {
      if (disposer !== undefined) {
        disposer();
      }
    };
  }, []);

  return (
    <Container>
      <Header instancesCount={filteredInstancesCount} />
      <List>
        <ScrollableContent
          overflow={shouldDisplaySkeleton ? 'hidden' : 'auto'}
          ref={scrollableContentRef}
        >
          {status === 'fetching' && <Spinner data-testid="instances-loader" />}

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

            <InfiniteScroller
              onVerticalScrollStartReach={async (scrollDown) => {
                if (
                  decisionInstancesStore.shouldFetchPreviousInstances() ===
                  false
                ) {
                  return;
                }

                await decisionInstancesStore.fetchPreviousInstances();

                if (hasLatestDecisionInstances) {
                  scrollDown(
                    latestFetch?.decisionInstancesCount ?? 0 * ROW_HEIGHT
                  );
                }
              }}
              onVerticalScrollEndReach={() => {
                if (
                  decisionInstancesStore.shouldFetchNextInstances() === false
                ) {
                  return;
                }

                decisionInstancesStore.fetchNextInstances();
              }}
              scrollableContainerRef={scrollableContentRef}
            >
              <DecisionInstances />
            </InfiniteScroller>
          </Table>
        </ScrollableContent>
      </List>
    </Container>
  );
});

export {InstancesTable};
