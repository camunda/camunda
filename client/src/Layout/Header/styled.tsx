/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
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

const Separator = styled.div`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      width: 1px;
      height: 24px;
      background-color: ${colors.separator};
      margin: 0 15px;
    `;
  }}
`;

export {BrandInfo, Brand, UserControls, AppName, Separator};
