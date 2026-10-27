/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {StateIcon as BaseStateIcon} from 'modules/components/StateIcon';
import {INSTANCE_HISTORY_LEFT_PADDING} from 'modules/constants';

const NodeName = styled.span`
  margin-left: var(--cds-spacing-02);
  align-self: center;
`;

const Container = styled.div`
  display: flex;
  justify-content: space-between;
  flex: 1;
`;

const StateIcon = styled(BaseStateIcon)`
  position: absolute;
  left: ${INSTANCE_HISTORY_LEFT_PADDING};
  align-self: center;
`;

export {NodeName, Container, StateIcon};
