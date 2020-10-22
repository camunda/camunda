/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

const PointerBasics = ({placement}) => {
  return css`
    position: absolute;
    border: solid transparent;
    content: ' ';
    pointer-events: none;
    z-index: 2;
    ${placement === DROPDOWN_PLACEMENT.TOP
      ? css`
          top: 100%;
          left: 25px;
        `
      : css`
          bottom: 100%;
          right: 15px;
        `};
  `;
};

const PointerBody = ({theme, placement}) => {
  const colors = theme.colors.modules.dropdown.menu.pointerBody;

  return css`
    border-width: 7px;
    margin-right: -7px;
    border-bottom-color: ${colors.borderColor};
    ${placement === DROPDOWN_PLACEMENT.TOP
      ? css`
          transform: rotate(180deg);
        `
      : ''};
  `;
};

const PointerShadow = ({theme, placement}) => {
  const colors = theme.colors.modules.dropdown.menu.pointerShadow;

  return css`
    border-width: 8px;
    border-bottom-color: ${colors.borderColor};
    ${placement === DROPDOWN_PLACEMENT.TOP
      ? css`
          transform: rotate(180deg);
        `
      : ''};
  `;
};

const Ul = styled.ul`
  ${({theme, placement}) => {
    const colors = theme.colors.modules.dropdown.menu.ul;
    const shadow = theme.shadows.modules.dropdown.menu.ul;
    const isTop = placement === DROPDOWN_PLACEMENT.TOP;

    return css`
      ${isTop
        ? css`
            position: relative;
            right: -115px;
            bottom: 155px;
          `
        : css`
            position: absolute;
            right: -1px;
          `}
      min-width: 186px;
      margin-top: 5px;
      padding-left: 0px;
      box-shadow: ${shadow};
      border: 1px solid ${colors.borderColor};
      border-radius: 3px;
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
    `;
  }}
`;

const topPointer = ({theme}) => {
  const colors = theme.colors.modules.dropdown.menu.topPointer;

  return css`
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
        border-bottom-color: ${colors.borderColor};
      }
    }

    &:first-child:active {
      &:after {
        border-bottom-color: ${theme.colors.active};
      }
    }
  `;
};

const bottomPointer = ({theme}) => {
  const colors = theme.colors.modules.dropdown.menu.bottomPointer;

  return css`
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
        border-bottom-color: ${colors.borderColor};
      }
    }

    &:last-child:active {
      &:after {
        border-bottom-color: ${theme.colors.active};
      }
    }
  `;
};

const Li = styled.li`
  ${({theme, placement}) => {
    const colors = theme.colors.modules.dropdown.menu.li;

    return css`
      ${placement === DROPDOWN_PLACEMENT.TOP ? bottomPointer : topPointer}

      &:not(:last-child) {
        border-bottom: 1px solid ${colors.borderColor};
      }
      &:first-child:last-child > div > button {
        border-radius: 2px;
      }
      &:last-child > div > button {
        border-radius: 0 0 2px 2px;
      }
      &:first-child > div > button {
        border-radius: 2px 2px 0 0;
      }
    `;
  }}
`;

export {PointerBasics, PointerBody, PointerShadow, Ul, Li};
