/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Tile as BaseTitle} from '@carbon/react';
import {styles} from '@carbon/elements';
import {Link} from 'modules/components/Link';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/ErrorMessage';

type GridProps = {
  $numberOfColumns: 1 | 2;
};

const Grid = styled.div<GridProps>`
  ${({$numberOfColumns}) => {
    return css`
      width: 100%;
      height: 100%;
      padding: var(--cds-spacing-05);
      display: grid;
      grid-template-rows: 158px 1fr;
      grid-gap: var(--cds-spacing-05);
      ${$numberOfColumns === 2
        ? css`
            grid-template-columns: 1fr 1fr;
            & > ${Tile}:first-of-type {
              grid-column-start: 1;
              grid-column-end: 3;
            }
          `
        : css`
            grid-template-columns: 1fr;
          `}
    `;
  }}
`;

const ScrollableContent = styled.div`
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  flex: 1;
`;

const Tile = styled(BaseTitle)`
  display: flex;
  flex-direction: column;
  border: 1px solid var(--cds-border-subtle-01);
`;

const TileTitle = styled.h2`
  ${styles.productiveHeading02};
  color: var(--cds-text-primary);
  margin-bottom: var(--cds-spacing-06);
`;

const LinkWrapper = styled(Link)`
  display: block;
  text-decoration: none !important;
  padding: var(--cds-spacing-03) 0;
`;

const ErrorMessage = styled(BaseErrorMessage)`
  margin: auto;
`;

const Li = styled.li`
  // override the hover color on expandable row's children
  &:hover {
    background-color: var(--cds-layer-hover);
  }
`;

const ErrorText = styled.span`
  ${styles.bodyCompact01};
  color: var(--cds-text-error);
`;

const LoadingContainer = styled.div`
  padding: var(--cds-spacing-05);
  text-align: center;
`;

export {
  Grid,
  ScrollableContent,
  Tile,
  TileTitle,
  LinkWrapper,
  ErrorMessage,
  Li,
  ErrorText,
  LoadingContainer,
};
