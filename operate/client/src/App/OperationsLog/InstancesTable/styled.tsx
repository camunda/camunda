/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import { black } from '@carbon/colors';

const Container = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const OperationLogName = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const PropertyText = styled.div`
  ${styles.caption01}
`;

const ActorTooltip = styled.div`
  --cds-layer: ${black};

  .cds--snippet--single {
    block-size: 2rem;
  }
`;

export {Container, OperationLogName, PropertyText, ActorTooltip};
