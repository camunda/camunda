/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useRef} from 'react';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {Search} from '@carbon/react/icons';
import {EmptyState} from 'modules/components/EmptyState';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {Skeleton} from '../Skeleton';
import {Bar} from '../ElementInstancesTree/Bar';
import {elementInstanceHistorySearchStore} from 'modules/stores/elementInstanceHistorySearch';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {tracking} from 'modules/tracking';
import {getForbiddenPermissionsError} from 'modules/constants/permissions';
import {
  ScrollContainer,
  List,
  RowButton,
  StatusRegion,
  EmptyStateContainer,
} from './styled';

const INSTANCE_HISTORY_FORBIDDEN = getForbiddenPermissionsError(
  'Instance History',
  'this instance history',
);

type RowProps = {
  item: ElementInstance;
  businessObjects: BusinessObjects;
};

const Row: React.FC<RowProps> = observer(({item, businessObjects}) => {
  const rowRef = useRef<HTMLDivElement>(null);
  const {selectElementInstance, isSelected} =
    useProcessInstanceElementSelection();

  const businessObject = businessObjects[item.elementId];
  const elementIsMultiInstanceBody = isMultiInstance(businessObject);
  const selected = isSelected({
    elementId: item.elementId,
    elementInstanceKey: item.elementInstanceKey,
    isMultiInstanceBody: elementIsMultiInstanceBody,
  });

  return (
    <li>
      <RowButton
        type="button"
        $selected={selected}
        data-testid={`search-result-${item.elementInstanceKey}`}
        onClick={() => {
          tracking.track({eventName: 'instance-history-item-clicked'});
          selectElementInstance({
            elementId: item.elementId,
            elementInstanceKey: item.elementInstanceKey,
            isMultiInstanceBody: elementIsMultiInstanceBody,
          });
        }}
      >
        <Bar
          ref={rowRef}
          elementInstanceKey={item.elementInstanceKey}
          elementId={item.elementId}
          elementName={item.elementName ?? item.elementId}
          elementInstanceState={item.state}
          hasIncident={item.hasIncident}
          endDate={item.endDate}
          isTimestampLabelVisible
          isRoot={false}
          latestMigrationDate={null}
          scopeKeyHierarchy={[]}
        />
      </RowButton>
    </li>
  );
});

type Props = {
  businessObjects: BusinessObjects;
};

const FilteredElementInstancesList: React.FC<Props> = observer(
  ({businessObjects}) => {
    const scrollableContainerRef = useRef<HTMLDivElement>(null);
    const {state} = elementInstanceHistorySearchStore;

    // Trigger the first fetch when the panel mounts with an active search
    // (e.g. when search text was already present from a prior interaction).
    useEffect(() => {
      if (
        elementInstanceHistorySearchStore.hasActiveSearch &&
        state.status === 'idle'
      ) {
        void elementInstanceHistorySearchStore.fetchFirstPage();
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    if (state.status === 'error-permissions') {
      return (
        <ErrorMessage
          message={INSTANCE_HISTORY_FORBIDDEN.message}
          additionalInfo={INSTANCE_HISTORY_FORBIDDEN.additionalInfo}
        />
      );
    }

    if (state.status === 'error') {
      return (
        <ErrorMessage
          message="Search results could not be fetched"
          additionalInfo="Refresh the page to try again"
        />
      );
    }

    if (state.status === 'loading' && state.items.length === 0) {
      return <Skeleton />;
    }

    if (state.status === 'loaded' && state.items.length === 0) {
      return (
        <EmptyStateContainer>
          <EmptyState
            heading="No matching elements"
            description="Try a different name or ID"
            icon={<Search size={32} />}
          />
        </EmptyStateContainer>
      );
    }

    return (
      <>
        <StatusRegion aria-live="polite">
          {state.pageMetadata.totalItems} matching elements
        </StatusRegion>
        <ScrollContainer ref={scrollableContainerRef}>
          <InfiniteScroller
            scrollableContainerRef={scrollableContainerRef}
            onVerticalScrollEndReach={() => {
              void elementInstanceHistorySearchStore.fetchNextPage();
            }}
            onVerticalScrollStartReach={() => {
              void elementInstanceHistorySearchStore.fetchPreviousPage();
            }}
          >
            <List>
              {state.items.map((item) => (
                <Row
                  key={item.elementInstanceKey}
                  item={item}
                  businessObjects={businessObjects}
                />
              ))}
            </List>
          </InfiniteScroller>
        </ScrollContainer>
      </>
    );
  },
);

export {FilteredElementInstancesList};
