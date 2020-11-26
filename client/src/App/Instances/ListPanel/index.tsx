/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {EXPAND_STATE} from 'modules/constants';
import SplitPane from 'modules/components/SplitPane';
import {filtersStore} from 'modules/stores/filters';
import {observer} from 'mobx-react';
import {instancesStore} from 'modules/stores/instances';

import List from './List';
import ListFooter from './ListFooter';
import * as Styled from './styled';

const ListPanel = observer((props: any) => {
  const {
    areWorkflowInstancesEmpty,
    state: {
      workflowInstances,
      isLoading: areInstancesLoading,
      isInitialLoadComplete: areInstancesInitiallyLoaded,
    },
  } = instancesStore;

  const getEmptyListMessage = () => {
    const {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'active' does not exist on type '{}'.
      filter: {active, incidents, completed, canceled},
    } = filtersStore.state;

    let msg = 'There are no instances matching this filter set.';

    if (!active && !incidents && !completed && !canceled) {
      msg += '\n To see some results, select at least one instance state.';
    }

    return msg;
  };

  const renderContent = () => {
    if (!areWorkflowInstancesEmpty) {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Body' does not exist on type 'typeof Lis... Remove this comment to see the full error message
      return <List.Body />;
    } else if (!areInstancesInitiallyLoaded) {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Skeleton' does not exist on type 'typeof... Remove this comment to see the full error message
      return <List.Skeleton />;
    } else {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Message' does not exist on type 'typeof ... Remove this comment to see the full error message
      return <List.Message message={getEmptyListMessage()} />;
    }
  };

  const renderSpinner = () => {
    return (
      areInstancesLoading &&
      areInstancesInitiallyLoaded &&
      (() => <Styled.Spinner />)
    );
  };

  return (
    <SplitPane.Pane {...props} hasShiftableControls>
      <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>

      <Styled.PaneBody>
        <List
          data={workflowInstances}
          expandState={props.expandState}
          isDataLoaded={!areInstancesLoading}
          Overlay={renderSpinner()}
        >
          {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'Header' does not exist on type 'typeof L... Remove this comment to see the full error message */}
          <List.Header />
          {renderContent()}
        </List>
      </Styled.PaneBody>
      <SplitPane.Pane.Footer>
        <ListFooter
          hasContent={
            props.expandState !== EXPAND_STATE.COLLAPSED &&
            !areWorkflowInstancesEmpty
          }
        />
      </SplitPane.Pane.Footer>
    </SplitPane.Pane>
  );
});

export {ListPanel};
