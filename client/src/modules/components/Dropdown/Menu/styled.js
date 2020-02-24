/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

const PointerBottom = css`
  top: 100%;
  left: 15px;
`;
const PointerTop = css`
  bottom: 100%;
  right: 15px;
`;

export const PointerBasics = css`
  position: absolute;
  border: solid transparent;
  content: ' ';
  pointer-events: none;
  z-index: 2;
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? PointerBottom : PointerTop};
`;

export const PointerBody = css`
  border-width: 7px;
  margin-right: -7px;
  border-bottom-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP && 'transform: rotate(180deg)'};
`;

export const PointerShadow = css`
  border-width: 8px;

  border-bottom-color: ${themeStyle({
    dark: Colors.uiDark06,
    light: Colors.uiLight05
  })};

  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP && 'transform: rotate(180deg)'};
`;

const placementStyle = (placement, topStyle, bottomStyle) =>
  placement === DROPDOWN_PLACEMENT.TOP ? topStyle : bottomStyle;

export const Ul = themed(styled.ul`
  /* Positioning */
  position: ${({placement}) =>
    placementStyle(placement, 'relative', 'absolute')};
  right: ${({placement}) => placementStyle(placement, '-115px', '-1px')};
  bottom: ${({placement}) => placementStyle(placement, '155px;', '')};
  top: ${({placement}) => placementStyle(placement, '20xp;', '')};

  /* Display & Box Model */
  min-width: 186px;
  margin-top: 5px;
  padding-left: 0px;
  box-shadow: 0 0 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: ' rgba(0, 0, 0, 0.2)'
    })};
  border: 1px solid
    ${themeStyle({dark: Colors.uiDark06, light: Colors.uiLight05})};
  border-radius: 3px;

  /* Color */
  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`);

const topPointer = css`
  &:first-child {
    /* Pointer Styles */
    &:after,
    &:before {
      ${PointerBasics};
    }

    &:after {
      ${PointerBody};
    }
    &:before {
      ${PointerShadow};
      margin-right: -8px;
    }
  }

  &:first-child:hover {
    &:after {
      border-bottom-color: ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight05
      })};
    }
  }

  &:first-child:active {
    &:after {
      border-bottom-color: ${themeStyle({
        dark: Colors.darkActive,
        light: Colors.lightActive
      })};
    }
  }
`;

const bottomPointer = css`
  /* Pointer Styles */
  &:last-child {
    &:after,
    &:before {
      ${PointerBasics};
    }

    &:after {
      ${PointerBody};
    }
    &:before {
      ${PointerShadow};
      margin-left: -1px;
    }
  }

  &:last-child:hover {
    &:after {
      border-bottom-color: ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight05
      })};
    }
  }

  &:last-child:active {
    &:after {
      border-bottom-color: ${themeStyle({
        dark: Colors.darkActive,
        light: Colors.lightActive
      })};
    }
  }
`;

export const Li = themed(styled.li`
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? bottomPointer : topPointer}

  /* Add Border between options */
  &:not(:last-child) {
    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight05
      })};
  }

  /* Border radius if only one child exists */
  &:first-child:last-child > div > button {
    border-radius: 2px 2px 2px 2px;
  }

  /* Border radius of child button in all states */
  &:last-child > div > button {
    border-radius: 0 0 2px 2px;
  }
  &:first-child > div > button {
    border-radius: 2px 2px 0 0;
  }
`);
