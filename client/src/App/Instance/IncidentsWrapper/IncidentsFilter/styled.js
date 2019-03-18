/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import Dropdown from 'modules/components/Dropdown';

export const FiltersWrapper = themed(styled.div`
  position: relative;
  padding: 21px 20px 26px;

  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};

  &:before {
    content: '';
    position: absolute;
    top: 0px;
    left: -51px;
    width: 51px;
    height: 100%;

    background-color: ${themeStyle({
      dark: Colors.uiDark03,
      light: Colors.uiLight02
    })};
  }
`);

export const Content = styled.div`
  position: relative;
  left: -51px;
`;

export const FilterRow = styled.div`
  display: flex;
  &:first-child {
    margin-bottom: 17px;
  }
`;

export const Label = themed(styled.span`
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};
  position: relative;
  width: 120px;
  padding-right: 17px;
  margin-right: 18px;

  font-size: 15px;
  font-weight: bold;
  text-align: right;

  opacity: 0.9;

  &:after {
    content: '';
    position: absolute;
    top: calc(50% - 8px);
    right: 0;
    width: 1px;
    height: 16px;

    background-color: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  }
`);

export const Ul = styled.ul`
  margin: 0;
  padding: 0;
  li {
    display: inline-block;
    margin-right: 10px;
  }
`;

export const MoreDropdown = themed(styled(Dropdown)`
  [data-test='dropdown-toggle'] {
    display: flex;
    align-items: center;

    border-radius: 16px;
    font-size: 13px;
    padding: 3px 10px;

    background-color: ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};
    border: 1px solid
      ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight03
      })};
    color: 1px solid
      ${themeStyle({
        dark: Colors.uiDark02,
        light: Colors.uiDark04
      })};
  }
  // style drop down
  [data-test='menu'] {
    margin-top: 9px;
    width: 235px;
    padding: 0 6px;
    z-index: 4;
  }
  li {
    width: 100%;
    &:not(:last-child) {
      border-bottom: 0;
    }

    &:first-child {
      &:before {
        margin-right: -4px;
      }
      &:after {
        margin-right: -3px;
      }

      &:hover {
        &:after {
          border-bottom-color: ${Colors.uiDark04};
        }
      }
    }
  }
  button {
    border-radius: 16px !important;
  }
`);
