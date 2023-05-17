/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Container, PanelHeader} from './styled';
import {TimeStampPill} from './TimeStampPill';
import {modificationsStore} from 'modules/stores/modifications';

const FlowNodeInstanceLog: React.FC = observer(() => {
  return (
    <Container>
      <PanelHeader title="Instance History" size="sm">
        {!modificationsStore.isModificationModeEnabled && <TimeStampPill />}
      </PanelHeader>
      <section>instance history</section>
    </Container>
  );
});

export {FlowNodeInstanceLog};
