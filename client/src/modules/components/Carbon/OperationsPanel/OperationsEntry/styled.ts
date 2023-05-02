/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {ProgressBar as BaseProgressBar} from '@carbon/react';

type Props = {
  isRunning?: boolean;
};

const Container = styled.li<Props>`
  ${({isRunning}) => {
    return css`
      padding: var(--cds-spacing-05);
      ${styles.bodyCompact01};
      border-bottom: 1px solid var(--cds-border-subtle-01);

      ${isRunning &&
      css`
        background-color: var(--cds-layer-hover-01);
      `}
    `;
  }}
`;

const Header = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--cds-spacing-02);
`;

const Title = styled.h3`
  ${styles.productiveHeading01};
  margin: 0;
`;

const Details = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  padding-top: var(--cds-spacing-07);
`;

const ProgressBar = styled(BaseProgressBar)`
  padding-top: var(
    --cds-spacing-06
  ); // empty label has 0.5rem margin-bottom, so this spacing should be 06 instead of 07
`;

export {Title, Details, Header, Container, ProgressBar};
