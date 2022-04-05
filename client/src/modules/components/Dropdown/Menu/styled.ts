/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

type Props = {
  $placement?: 'top' | 'bottom';
};

const PointerBasics: ThemedInterpolationFunction<Props> = ({$placement}) => {
  return css`
    position: absolute;
    border: solid transparent;
    content: ' ';
    pointer-events: none;
    z-index: 2;
    ${$placement === 'top'
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

const PointerBody: ThemedInterpolationFunction<Props> = ({
  theme,
  $placement,
}) => {
  const colors = theme.colors.modules.dropdown.menu.pointerBody;

  return css`
    border-width: 7px;
    margin-right: -7px;
    border-bottom-color: ${colors.borderColor};
    ${$placement === 'top'
      ? css`
          transform: rotate(180deg);
        `
      : ''};
  `;
};

const PointerShadow: ThemedInterpolationFunction<Props> = ({
  theme,
  $placement,
}) => {
  const colors = theme.colors.modules.dropdown.menu.pointerShadow;

  return css`
    border-width: 8px;
    border-bottom-color: ${colors.borderColor};
    ${$placement === 'top'
      ? css`
          transform: rotate(180deg);
        `
      : ''};
  `;
};

const Ul = styled.ul<Props>`
  ${({theme, $placement}) => {
    const colors = theme.colors.modules.dropdown.menu.ul;
    const shadow = theme.shadows.modules.dropdown.menu.ul;
    const isTop = $placement === 'top';

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
      color: ${theme.colors.text02};
    `;
  }}
`;

const topPointer: ThemedInterpolationFunction<Props> = ({theme}) => {
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
        border-bottom-color: ${theme.colors.menuActive};
      }
    }
  `;
};

const bottomPointer: ThemedInterpolationFunction<Props> = ({theme}) => {
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
        border-bottom-color: ${theme.colors.menuActive};
      }
    }
  `;
};

const Li = styled.li<Props>`
  ${({theme, $placement}) => {
    const colors = theme.colors.modules.dropdown.menu.li;

    return css`
      ${$placement === 'top' ? bottomPointer : topPointer}

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
