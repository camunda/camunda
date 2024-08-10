/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
