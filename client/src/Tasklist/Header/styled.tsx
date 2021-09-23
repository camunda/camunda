/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Link} from 'react-router-dom';
import {Dropdown} from './Dropdown';

const BrandInfo = styled.div`
  display: flex;
  align-items: center;
`;

const Brand = styled(Link)`
  display: flex;
  align-items: center;
  color: ${({theme}) => theme.colors.ui06};
  font-size: 15px;
  font-weight: 500;
`;

const UserControls = styled(Dropdown)`
  margin-top: 3px;
  margin-left: auto;
`;

const AppName = styled.div`
  margin-left: 19px;
`;

export {BrandInfo, Brand, UserControls, AppName};
