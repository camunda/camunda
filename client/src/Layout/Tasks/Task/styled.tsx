/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {rem} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';
import {NavLink} from 'react-router-dom';
import styled, {css} from 'styled-components';

const ENTRY_DEFAULT_BORDER_WIDTH = 1;
const ENTRY_SELECTED_BORDER_WIDTH = 4;
const ENTRY_FOCUSED_BORDER_WIDTH = 2;

function getEntryPadding(options?: {
  top?: number;
  right?: number;
  bottom?: number;
  left?: number;
}) {
  const {top = 0, right = 0, bottom = 0, left = 0} = options ?? {};

  return css`
    ${({theme}) =>
      css`
        padding: calc(${theme.spacing05} - ${top}px)
          calc(${theme.spacing05} - ${right}px)
          calc(${theme.spacing05} - ${bottom}px)
          calc(${theme.spacing05} - ${left}px);
      `}
  `;
}

const Name = styled.span`
  ${({theme}) =>
    css`
      color: var(--cds-text-primary);
      ${theme.bodyShort02};
    `}
`;

const Process = styled.span`
  ${({theme}) => css`
    color: var(--cds-text-secondary);
    ${theme.label01};
  `}
`;

const Assignee = styled.span`
  ${({theme}) =>
    css`
      color: var(--cds-text-secondary);
      ${theme.bodyShort01};
    `}
`;

const CreationTime = styled.span`
  ${({theme}) =>
    css`
      color: var(--cds-text-secondary);
      ${theme.label01};
    `}
`;

const Row = styled.div`
  &:nth-child(1) {
    display: flex;
    flex-direction: column;
  }

  &:nth-child(2) {
    display: flex;
    justify-content: space-between;
  }
`;

const TaskLink = styled(NavLink)`
  all: unset;
  display: flex;
  align-items: stretch;
  box-sizing: border-box;
`;

const Stack = styled(BaseStack)`
  width: 100%;
`;

const Li = styled.li`
  cursor: pointer;

  &.active ${TaskLink} {
    background-color: var(--cds-layer-selected);
    border-left: ${ENTRY_SELECTED_BORDER_WIDTH}px solid
      var(--cds-border-interactive);
    ${getEntryPadding({
      left: ENTRY_SELECTED_BORDER_WIDTH,
    })}
  }

  &.active:last-child ${TaskLink} {
    ${getEntryPadding({
      left: ENTRY_SELECTED_BORDER_WIDTH,
    })}
  }

  &.active + & ${TaskLink}:not(:focus) {
    border-top: none;
    ${getEntryPadding()}
  }

  &:not(.active) {
    &:hover ${TaskLink} {
      background-color: var(--cds-layer-hover);
    }

    &:last-child ${TaskLink} {
      border-bottom: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
        var(--cds-border-subtle-selected);
      ${getEntryPadding({
        top: ENTRY_DEFAULT_BORDER_WIDTH,
        bottom: ENTRY_DEFAULT_BORDER_WIDTH,
      })}
    }

    & ${TaskLink} {
      border-top: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
        var(--cds-border-subtle-selected);
      ${getEntryPadding({
        top: ENTRY_DEFAULT_BORDER_WIDTH,
      })}
    }
  }

  & ${TaskLink}:focus {
    border: none;
    ${getEntryPadding()}
    outline: ${ENTRY_FOCUSED_BORDER_WIDTH}px solid var(--cds-focus);
    outline-offset: -${ENTRY_FOCUSED_BORDER_WIDTH}px;
  }

  &:last-child ${TaskLink}:focus {
    ${getEntryPadding()}
  }

  &:first-child ${TaskLink} {
    border-top-color: transparent;
  }
`;

const SkeletonLi = styled.li`
  min-height: ${rem(128)};
  max-height: ${rem(128)};

  &:last-child > * {
    border-bottom: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
      var(--cds-border-subtle-selected);
    ${getEntryPadding({
      top: ENTRY_DEFAULT_BORDER_WIDTH,
      bottom: ENTRY_DEFAULT_BORDER_WIDTH,
    })}
  }

  & > * {
    border-top: ${ENTRY_DEFAULT_BORDER_WIDTH}px solid
      var(--cds-border-subtle-selected);
    ${getEntryPadding({
      top: ENTRY_DEFAULT_BORDER_WIDTH,
    })}
  }
`;

export {
  Row,
  Name,
  Process,
  Assignee,
  CreationTime,
  TaskLink,
  Stack,
  Li,
  SkeletonLi,
};
