/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Spinner from 'modules/components/Spinner';

const Skeleton = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.spinnerSkeleton.skeleton;

    return css`
      background-color: ${colors.backgroundColor};
      z-index: 2;
      display: flex;
      justify-content: center;
      align-items: center;
      width: 100%;
      height: 100%;
      position: absolute;
    `;
  }}
`;

const SkeletonSpinner = styled(Spinner)`
  ${({theme}) => {
    const colors = theme.colors.modules.spinnerSkeleton.skeletonSpinner;

    return css`
      position: absolute;
      top: 40.7%;
      height: 30px;
      width: 30px;
      border: 4px solid ${colors.borderColor};
      border-right-color: transparent;
    `;
  }}
`;

export {Skeleton, SkeletonSpinner};
