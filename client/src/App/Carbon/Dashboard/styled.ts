/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Tile as BaseTitle} from '@carbon/react';
import {styles} from '@carbon/elements';
import {Link} from 'modules/components/Carbon/Link';

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
`;

export {Grid, ScrollableContent, Tile, TileTitle, LinkWrapper};
