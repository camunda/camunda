/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {INSTANCE_HISTORY_LEFT_PADDING} from 'modules/constants';

type Props = {
  SVGComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement>>;
  $isGateway: boolean;
  className?: string;
  $hasLeftMargin?: boolean;
};

const BaseSVGIcon: React.FC<Props> = ({SVGComponent, className}) => {
  return <SVGComponent className={className} />;
};

const SVGIcon = styled(BaseSVGIcon)`
  ${({$isGateway, $hasLeftMargin}) => {
    return css`
      ${$isGateway
        ? css`
            position: relative;
            top: 3px;
            right: 2px;
          `
        : ''}
      ${$hasLeftMargin
        ? css`
            margin-left: ${INSTANCE_HISTORY_LEFT_PADDING};
          `
        : ''}
    `;
  }}
`;

export {SVGIcon};
