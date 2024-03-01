/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {InlineNotification as BaseInlineNotification} from '@carbon/react';

const PanelHeader = styled(BasePanelHeader)`
  padding-right: 0;
`;

const Section = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

// custom styling to set height to 32px
const InlineNotification = styled(BaseInlineNotification)`
  min-block-size: 32px;
  max-block-size: 32px;
  max-inline-size: unset;

  .cds--inline-notification__icon {
    margin-block-start: unset;
  }

  .cds--inline-notification__text-wrapper {
    padding: unset;
  }

  .cds--inline-notification__details {
    align-items: center;
  }
`;

export {PanelHeader, Section, InlineNotification};
