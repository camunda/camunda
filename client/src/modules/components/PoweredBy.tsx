/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Link, SkeletonPlaceholder} from '@carbon/react';
import {BodyCompact as BaseBodyCompact} from './FontTokens';
import {CamundaLogo as BaseCamundaLogo} from 'modules/components/CamundaLogo';

const BodyCompact = styled(BaseBodyCompact)`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-03);
`;

const CamundaLogo = styled(BaseCamundaLogo)`
  width: 70px;
`;

const SkeletonFooterText = styled(SkeletonPlaceholder)`
  width: 75px;
  height: 16px;
`;

const SkeletonLogo = styled(SkeletonPlaceholder)`
  width: 70px;
  height: 24px;
`;

type PoweredByProps = {
  className?: string;
};

const PoweredBy: React.FC<PoweredByProps> = ({className}) => {
  return (
    <BodyCompact as="p" className={className}>
      Powered by{' '}
      <Link href="https://camunda.com/" target="_blank">
        <CamundaLogo aria-label="Camunda" />
      </Link>
    </BodyCompact>
  );
};

const SkeletonPoweredBy: React.FC = () => {
  return (
    <BodyCompact>
      <SkeletonFooterText />
      <SkeletonLogo />
    </BodyCompact>
  );
};

export {PoweredBy, SkeletonPoweredBy};
