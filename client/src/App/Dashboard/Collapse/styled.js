/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import BasicExpandButton from 'modules/components/ExpandButton';

export const Collapse = styled.div`
  position: relative;
`;

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  top: 14px;
  left: 0;
`;
