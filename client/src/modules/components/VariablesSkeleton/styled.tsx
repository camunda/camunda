/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {SkeletonPlaceholder} from '@carbon/react';

const TitleSkeleton = styled(SkeletonPlaceholder)`
  ${({theme}) => css`
    width: 85px;
    height: 32px;
    margin: 0 ${theme.spacing05};
  `}
`;

export {TitleSkeleton};
