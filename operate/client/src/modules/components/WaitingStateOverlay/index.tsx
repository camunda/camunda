/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Container} from './styled';

type Props = {
  container: HTMLElement;
  label: string;
  centered?: boolean;
};

const WaitingStateOverlay: React.FC<Props> = ({container, label, centered}) => {
  return createPortal(
    <Container data-testid="waiting-state-overlay" $centered={centered}>
      <span>{label}</span>
    </Container>,
    container,
  );
};

export {WaitingStateOverlay};
