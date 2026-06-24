/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Container} from './styled';
import {Time} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {currentTheme} from 'modules/stores/currentTheme';

type Props = {
  container: HTMLElement;
  label: string;
};

const WaitingStateOverlay: React.FC<Props> = observer(({container, label}) => {
  return createPortal(
    <Container
      data-testid="waiting-state-overlay"
      $theme={currentTheme.theme}
      orientation="horizontal"
      gap={2}
    >
      <Time size={14} />
      <span>{label}</span>
    </Container>,
    container,
  );
});

export {WaitingStateOverlay};
