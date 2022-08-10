/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Container, Text, Button} from './styled';
import {modificationsStore} from 'modules/stores/modifications';

const MoveTokenBanner: React.FC = () => {
  return (
    <Container>
      <Text>Select the target flow node in the diagram</Text>
      <Button
        onClick={() => {
          modificationsStore.finishMovingToken();
        }}
      >
        Discard
      </Button>
    </Container>
  );
};

export {MoveTokenBanner};
