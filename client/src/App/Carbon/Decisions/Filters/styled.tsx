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
`;

const Container = styled.div`
  padding: 0 ${spacing05};
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
`;

export {Container, Title, WarningFilled};
