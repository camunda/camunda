/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {NavLink as BaseLink} from 'react-router-dom';

const Container = styled.li`
  display: flex;
  align-items: center;
  font-weight: normal;
  &:first-child {
    font-weight: 500;
  }
  &:not(:first-child) > .active {
    font-weight: 600;
  }
`;

const Label = styled.span`
  margin: 0 20px;
`;

const Link = styled(BaseLink)`
  display: flex;
  align-items: center;
`;

export {Container, Label, Link};
