/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/ErrorMessage';
import {Link} from 'modules/components/Link';

const Title = styled(Link)`
  ${styles.productiveHeading04};
  color: var(--cds-text-primary) !important;
  display: inline-block;
  margin-bottom: var(--cds-spacing-05);
`;

const LabelContainer = styled.div`
  width: 100%;
  display: flex;
  justify-content: space-between;
`;

const Label = styled(Link)`
  ${styles.productiveHeading03};
  color: var(--cds-text-primary) !important;
`;

const ErrorMessage = styled(BaseErrorMessage)`
  height: 100%;
  justify-content: center;
  align-content: center;
  margin: auto;
`;

export {Title, LabelContainer, Label, ErrorMessage};
