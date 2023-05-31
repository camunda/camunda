/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

type Props = {
  SVGComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement>>;
  $isGateway: boolean;
  className?: string;
};

const BaseSVGIcon: React.FC<Props> = ({SVGComponent, className}) => {
  return <SVGComponent className={className} />;
};

const SVGIcon = styled(BaseSVGIcon)`
  ${({$isGateway}) => {
    return css`
      ${$isGateway
        ? css`
            position: relative;
            top: 3px;
            right: 2px;
          `
        : ''}
    `;
  }}
`;

export {SVGIcon};
