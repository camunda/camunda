/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {EXPAND_STATE} from 'modules/constants';
import SplitPane from 'modules/components/SplitPane';
import {filters} from 'modules/stores/filters';
import {observer} from 'mobx-react';
import {instances} from 'modules/stores/instances';

import List from './List';
import ListFooter from './ListFooter';
import * as Styled from './styled';

const ListPanel = observer((props) => {
  const {
    areWorkflowInstancesEmpty,
    state: {
      workflowInstances,
      isLoading: areInstancesLoading,
      isInitialLoadComplete: areInstancesInitiallyLoaded,
    },
  } = instances;

  const handleOperationButtonClick = (instance) => {
    instances.addInstancesWithActiveOperations({ids: [instance.id]});
  };

  const getEmptyListMessage = () => {
    const {
      filter: {active, incidents, completed, canceled},
    } = filters.state;

    let msg = 'There are no instances matching this filter set.';

    if (!active && !incidents && !completed && !canceled) {
      msg += '\n To see some results, select at least one instance state.';
    }

    return msg;
  };

  const renderContent = () => {
    if (!areWorkflowInstancesEmpty) {
      return <List.Body />;
    } else if (!areInstancesInitiallyLoaded) {
      return <List.Skeleton />;
    } else {
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
          onOperationButtonClick={handleOperationButtonClick}
          Overlay={renderSpinner()}
        >
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
