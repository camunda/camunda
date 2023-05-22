/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {heading01, spacing05, supportError} from '@carbon/elements';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';

const Title = styled.h3`
  ${heading01};
  padding-bottom: ${spacing05};
`;

const Container = styled.div`
  padding: ${spacing05};
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
`;

const ButtonContainer = styled.div`
  position: absolute;
  display: none;
  right: 0;
  top: -8px;
`;

const FieldContainer = styled.div`
  position: relative;
  &:hover {
    ${ButtonContainer} {
      display: block;
    }
  }
`;

export {Container, Title, WarningFilled, FieldContainer, ButtonContainer};
