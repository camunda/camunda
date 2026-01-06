/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {INSTANCE_HISTORY_LEFT_PADDING} from 'modules/constants';

type Props = {
  SVGComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement>>;
  $isGateway: boolean;
  className?: string;
  $hasLeftMargin?: boolean;
};

const BaseSVGIcon: React.FC<Props> = ({SVGComponent, className, ...rest}) => {
  return <SVGComponent className={className} {...rest} />;
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
