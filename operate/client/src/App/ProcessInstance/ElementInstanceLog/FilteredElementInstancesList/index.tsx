/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useMemo, useRef} from 'react';
import type {
  ElementInstance,
  QueryElementInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {Search} from '@carbon/react/icons';
import {EmptyState} from 'modules/components/EmptyState';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {Skeleton} from '../Skeleton';
import {Bar} from '../ElementInstancesTree/Bar';
import {ElementInstanceIcon} from 'modules/components/ElementInstanceIcon';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {tracking} from 'modules/tracking';
import {getForbiddenPermissionsError} from 'modules/constants/permissions';
import {useElementInstancesSearchPaginated} from 'modules/queries/elementInstances/useElementInstancesSearchPaginated';
import {flattenPaginatedPages} from 'modules/queries/flattenPaginatedPages';
import {useDashboardScrollPagination} from 'App/Dashboard/useDashboardScrollPagination';
import {escapeLikePatternsForCaseInsensitive} from 'modules/utils/escapeLikePattern';
import {isRequestError} from 'modules/request';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {
  ScrollContainer,
  List,
  RowButton,
  IconSlot,
  StatusRegion,
  EmptyStateContainer,
} from './styled';

const INSTANCE_HISTORY_FORBIDDEN = getForbiddenPermissionsError(
  'Instance History',
  'this instance history',
);

const PAGE_LIMIT = 50;

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
        <IconSlot>
          <ElementInstanceIcon
            diagramBusinessObject={businessObject}
            isRootProcess={item.type === 'PROCESS'}
          />
        </IconSlot>
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
  searchText: string;
  processInstanceKey: string;
  businessObjects: BusinessObjects;
};

const FilteredElementInstancesList: React.FC<Props> = ({
  searchText,
  processInstanceKey,
  businessObjects,
}) => {
  const scrollableContainerRef = useRef<HTMLDivElement>(null);

  const payload = useMemo(() => {
    const patterns = escapeLikePatternsForCaseInsensitive(searchText);
    const orClauses = patterns.flatMap((pattern) => [
      {elementName: {$like: pattern}},
      {elementId: {$like: pattern}},
    ]);
    return {
      filter: {
        processInstanceKey,
        $or: orClauses,
      },
      sort: [{field: 'startDate' as const, order: 'asc' as const}],
    } as QueryElementInstancesRequestBody;
  }, [searchText, processInstanceKey]);

  const query = useElementInstancesSearchPaginated({
    payload,
    select: flattenPaginatedPages,
  });

  const scroll = useDashboardScrollPagination(query, PAGE_LIMIT);

  const isForbiddenError =
    query.status === 'error' &&
    isRequestError(query.error) &&
    query.error?.response?.status === HTTP_STATUS_FORBIDDEN;

  if (isForbiddenError) {
    return (
      <ErrorMessage
        message={INSTANCE_HISTORY_FORBIDDEN.message}
        additionalInfo={INSTANCE_HISTORY_FORBIDDEN.additionalInfo}
      />
    );
  }

  if (query.status === 'error') {
    return (
      <ErrorMessage
        message="Search results could not be fetched"
        additionalInfo="Refresh the page to try again"
      />
    );
  }

  if (query.status === 'pending') {
    return <Skeleton />;
  }

  if (query.data?.items.length === 0) {
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
        {query.data?.totalCount ?? 0} matching elements
      </StatusRegion>
      <ScrollContainer ref={scrollableContainerRef}>
        <InfiniteScroller
          scrollableContainerRef={scrollableContainerRef}
          onVerticalScrollEndReach={scroll.handleScrollEndReach}
          onVerticalScrollStartReach={scroll.handleScrollStartReach}
        >
          <List>
            {query.data?.items.map((item) => (
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
};

export {FilteredElementInstancesList};
