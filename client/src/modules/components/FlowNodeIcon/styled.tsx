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
  ${({theme, $isGateway}) => {
    return css`
      position: relative;
      color: ${theme.colors.text02};
      top: 0px;
      left: 3px;

      ${$isGateway
        ? css`
            top: 3px;
            left: 0;
          `
        : ''}
    `;
  }}
`;

export {SVGIcon};
