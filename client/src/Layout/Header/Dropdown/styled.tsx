/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Container = styled.div`
  position: relative;
  width: fit-content;
`;
interface ButtonProps {
  onKeyDown: (e: React.KeyboardEvent<Element>) => void;
}

const Button = styled.button<ButtonProps>`
  display: flex;
  align-items: center;
  padding: 0px;
  color: ${({theme}) => theme.colors.ui06};
  background: none;
  font-family: IBM Plex Sans;
  font-size: 15px;
  font-weight: 600;
`;

const LabelWrapper = styled.div`
  margin-right: 8px;
  color: ${({theme}) => theme.colors.ui06};
  background: none;
  font-family: IBM Plex Sans;
  font-size: 15px;
  font-weight: 600;
`;

const Menu = styled.ul`
  position: absolute;
  right: -8px;
  min-width: 186px;
  margin-top: 5px;
  padding-left: 0px;
  box-shadow: ${({theme}) => theme.shadows.dropdownMenu};
  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-radius: 3px;
  background-color: ${({theme}) => theme.colors.ui02};
`;

export {Container, Button, LabelWrapper, Menu};
