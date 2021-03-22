/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {EXPAND_STATE} from 'modules/constants';
import SplitPane from 'modules/components/SplitPane';
import {observer} from 'mobx-react';
import {instancesStore} from 'modules/stores/instances';
import {useLocation} from 'react-router-dom';
import List from './List';
import ListFooter from './ListFooter';
import {Spinner, PaneBody} from './styled';
import {StatusMessage} from 'modules/components/StatusMessage';
import {getFilters} from 'modules/utils/filter';

const ListPanel = observer((props: any) => {
  const {
    areWorkflowInstancesEmpty,
    state: {workflowInstances, status},
  } = instancesStore;
  const location = useLocation();
  const filters = getFilters(location.search);

  const getEmptyListMessage = () => {
    const {active, incidents, completed, canceled} = filters;

    let msg = 'There are no Instances matching this filter set';

    if (!active && !incidents && !completed && !canceled) {
      msg += '\n To see some results, select at least one Instance state';
    }

    return msg;
  };

  const renderContent = () => {
    if (!areWorkflowInstancesEmpty) {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Body' does not exist on type 'typeof Lis... Remove this comment to see the full error message
      return <List.Body />;
    } else if (['initial', 'first-fetch'].includes(status)) {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Skeleton' does not exist on type 'typeof... Remove this comment to see the full error message
      return <List.Skeleton />;
    } else if (status === 'error') {
      return (
        //@ts-expect-error
        <List.Message
          message={
            <StatusMessage variant="error">
              Instances could not be fetched
            </StatusMessage>
          }
        />
      );
    } else {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Message' does not exist on type 'typeof ... Remove this comment to see the full error message
      return <List.Message message={getEmptyListMessage()} />;
    }
  };

  const renderSpinner = () => {
    return (
      status === 'fetching' && (() => <Spinner data-test="instances-loader" />)
    );
  };

  return (
    <SplitPane.Pane {...props} hasShiftableControls>
      <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>

      <PaneBody>
        <List
          data={workflowInstances}
          expandState={props.expandState}
          isInitialDataLoaded={[
            'fetched',
            'fetching-next',
            'fetching-prev',
          ].includes(status)}
          Overlay={renderSpinner()}
        >
          {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'Header' does not exist on type 'typeof L... Remove this comment to see the full error message */}
          <List.Header />
          {renderContent()}
        </List>
      </PaneBody>
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
