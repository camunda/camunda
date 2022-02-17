/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {EXPAND_STATE} from 'modules/constants';
import SplitPane from 'modules/components/SplitPane';
import List from './List';
import ListFooter from './ListFooter';
import {PaneBody, Title, InstancesCount} from './styled';
import {instancesStore} from 'modules/stores/instances';
import {Observer} from 'mobx-react';

type Props = {
  expandState?: keyof typeof EXPAND_STATE;
};

const ListPanel: React.FC<Props> = (props) => {
  return (
    <SplitPane.Pane {...props} hasShiftableControls>
      <SplitPane.Pane.Header>
        <Title>Instances</Title>
        <Observer>
          {() => (
            <>
              {instancesStore.state.filteredInstancesCount > 0 && (
                <InstancesCount data-testid="filtered-instances-count">
                  {instancesStore.state.filteredInstancesCount} results found
                </InstancesCount>
              )}
            </>
          )}
        </Observer>
      </SplitPane.Pane.Header>

      <PaneBody>
        <List expandState={props.expandState} />
      </PaneBody>
      <SplitPane.Pane.Footer>
        <ListFooter
          isCollapsed={props.expandState === EXPAND_STATE.COLLAPSED}
        />
      </SplitPane.Pane.Footer>
    </SplitPane.Pane>
  );
};

export {ListPanel};
