/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {List} from './List';
import {processInstancesStore} from 'modules/stores/processInstances';
import {Observer} from 'mobx-react';
import {PanelHeader} from 'modules/components/PanelHeader';
import {Container} from './styled';

const ListPanel: React.FC = () => {
  return (
    <Container>
      <Observer>
        {() => (
          <PanelHeader
            title="Process Instances"
            count={processInstancesStore.state.filteredProcessInstancesCount}
          />
        )}
      </Observer>

      <List />
    </Container>
  );
};

export {ListPanel};
