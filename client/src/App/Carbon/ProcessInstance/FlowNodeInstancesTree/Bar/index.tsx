/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {TimeStampLabel} from './TimeStampLabel';
import {NodeName, Container, StateIcon} from './styled';
import {Layer, Stack} from '@carbon/react';

import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {ModificationIcons} from './ModificationIcons';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  nodeName: string;
};

const Bar: React.FC<Props> = observer(({nodeName, flowNodeInstance}) => {
  return (
    <Container>
      <Stack orientation="horizontal" gap={5}>
        {flowNodeInstance.state !== undefined && (
          <StateIcon state={flowNodeInstance.state} size={16} />
        )}
        <NodeName>{nodeName}</NodeName>
        {!modificationsStore.isModificationModeEnabled && (
          <Layer>
            <TimeStampLabel timeStamp={flowNodeInstance.endDate} />
          </Layer>
        )}
      </Stack>

      <ModificationIcons flowNodeInstance={flowNodeInstance} />
    </Container>
  );
});

export {Bar};
