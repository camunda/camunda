/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';

type Props = {
  SVGComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement>>;
  $isGateway: boolean;
  className?: string;
};

const BaseSVGIcon: React.FC<Props> = ({SVGComponent, className, ...rest}) => {
  return <SVGComponent className={className} {...rest} />;
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
