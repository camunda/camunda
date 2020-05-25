/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

export const Dropdown = styled.div`
  position: relative;
`;
interface ButtonProps {
  onKeyDown: (e: React.KeyboardEvent<Element>) => void;
}

export const Button = styled.button<ButtonProps>`
  display: flex;
  align-items: center;
  padding-right: 0px;
  color: ${({theme}) => theme.colors.ui06};
  background: none;
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;
`;

export const LabelWrapper = styled.div`
  margin-right: 8px;
`;

export const Menu = styled.ul`
  /* Positioning */
  position: absolute;
  right: 0;

  /* Display & Box Model */
  min-width: 186px;
  margin-top: 5px;
  padding-left: 0px;
  box-shadow: 0 0 2px 0 rgba(0, 0, 0, 0.2);
  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-radius: 3px;

  /* Color */
  background-color: ${({theme}) => theme.colors.ui02};
  color: ${({theme}) => theme.colors.ui06};
`;
