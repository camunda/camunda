/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Warning} from 'modules/components/Warning';

const Container = styled.div`
  justify-content: center;
  display: flex;
  width: 20px;
`;

const WarningIcon = styled(Warning)`
  padding: 3px 0;
`;

export {Container, WarningIcon};
