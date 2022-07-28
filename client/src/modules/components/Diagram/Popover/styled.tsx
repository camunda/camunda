/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const Arrow = styled.div`
  &:before,
  &:after {
    position: absolute;
    content: ' ';
    pointer-events: none;
    border: 9px solid transparent;
  }
`;

const Container = styled.div`
  ${({theme}) => {
    const arrowStyle = theme.colors.modules.diagram.popover.arrowStyle;
    const colors = theme.colors.modules.diagram.popover;
    const shadow = theme.shadows.modules.diagram.popover;

    return css`
      background-color: ${colors.backgroundColor};
      border: 1px solid ${colors.borderColor};
      box-shadow: ${shadow};
      color: ${theme.colors.text02};
      ${styles.bodyShort01};
      font-size: 12px;
      border-radius: 3px;
      cursor: auto;

      &[data-popper-reference-hidden='true'] {
        visibility: hidden;
      }

      &[data-popper-placement^='top'] > ${Arrow} {
        bottom: 1px;
        &:before {
          left: calc(50% - 9px);
          border-top-color: ${arrowStyle.before.borderColor};
        }
        &:after {
          left: calc(50% - 8px);
          border-top-color: ${arrowStyle.after.borderColor};
        }
      }

      &[data-popper-placement^='bottom'] > ${Arrow} {
        top: -17px;
        &:before {
          left: calc(50% - 9px);
          border-bottom-color: ${arrowStyle.before.borderColor};
        }
        &:after {
          left: calc(50% - 8px);
          border-bottom-color: ${arrowStyle.after.borderColor};
        }
      }

      &[data-popper-placement^='right'] > ${Arrow} {
        left: -17px;
        &:before {
          top: calc(50% - 9px);
          border-right-color: ${arrowStyle.before.borderColor};
        }
        &:after {
          top: calc(50% - 8px);
          border-right-color: ${arrowStyle.after.borderColor};
        }
      }

      &[data-popper-placement^='left'] > ${Arrow} {
        right: 1px;
        &:before {
          top: calc(50% - 9px);
          border-left-color: ${arrowStyle.before.borderColor};
        }
        &:after {
          top: calc(50% - 8px);
          border-left-color: ${arrowStyle.after.borderColor};
        }
      }
    `;
  }}
`;

export {Container, Arrow};
