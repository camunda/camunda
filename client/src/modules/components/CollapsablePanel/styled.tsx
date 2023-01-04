/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import styled, {css} from 'styled-components';
import {RowCollapse} from '@carbon/react/icons';
import {rem} from '@carbon/elements';

const ExpandedPanel = styled.div`
  width: ${rem(478)};
  height: 100%;
`;

const CollapsedPanel = styled.div`
  ${({theme}) => css`
    all: unset;
    padding: ${theme.spacing04};
    width: ${rem(57)};
    height: 100%;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: start;
    flex-direction: column;
    gap: ${theme.spacing06};
    box-sizing: border-box;
  `}
`;

const Title = styled.h4`
  ${({theme}) =>
    css`
      color: var(--cds-text-secondary);
      transform: rotate(90deg);
      ${theme.productiveHeading02};
    `}
`;

const Container = styled.div`
  width: fit-content;
`;

const Expand = styled(RowCollapse)`
  transform: rotate(90deg);
`;

const Collapse = styled(RowCollapse)`
  transform: rotate(-90deg);
`;

export {ExpandedPanel, CollapsedPanel, Title, Container, Expand, Collapse};
