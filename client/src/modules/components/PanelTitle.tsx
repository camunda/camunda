/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

type Props = {
  $isVertical?: boolean;
};

const Title = styled.h2<Props>`
  ${({$isVertical}) => {
    return css`
      margin: 0;
      ${styles.headingCompact01};
      color: var(--cds-text-primary);

      ${$isVertical &&
      css`
        writing-mode: vertical-lr;
        transform: rotate(-180deg);
      `}
    `;
  }}
`;

export {Title};
