/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {ReactComponent as Logo} from 'modules/icons/logo-2020-round.svg';
import {Link} from 'react-router-dom';
import {Dropdown} from './Dropdown';

export const LogoIcon = styled(Logo)`
  width: 26px;
  height: 26px;
  margin-right: 19px;
`;
export const BrandInfo = styled.div`
  display: flex;
  align-items: center;
`;
export const Brand = styled(Link)`
  display: flex;
  align-items: center;
  color: ${({theme}) => theme.colors.ui06};
`;
export const UserControls = styled(Dropdown)`
  margin-top: 3px;
  margin-left: auto;
`;
export const HeaderContent = styled.nav`
  display: flex;
  padding: 15px 20px 15px 20px;
  font-size: 15px;
  font-weight: 500;
  background-color: ${({theme}) => theme.colors.ui01};
  justify-content: space-between;
`;
