/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {ActionableNotification, Link} from '@carbon/react';

const ActionableNotificationStyles = styled(ActionableNotification)`
  max-width: unset;

  .cds--actionable-notification__details {
    margin-inline-end: var(--cds-spacing-05);

    .cds--actionable-notification__text-wrapper {
      display: block;
      flex: 1;

      .cds--actionable-notification__content {
        .cds--actionable-notification__subtitle {
          float: left;
        }
      }
    }
  }
`;

const DocumentationLink = styled(Link)`
  float: right;
`;

const NotificationWrapper = styled.div`
  padding: var(--cds-spacing-05);
`;

export {DocumentationLink, ActionableNotificationStyles, NotificationWrapper};
