/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MigrationSummary, InlineNotification} from './styled';
import {MigrationDetails} from '../MigrationDetails';

const MigrationSummaryNotification: React.FC = () => {
  return (
    <InlineNotification
      kind="info"
      title=""
      children={
        <MigrationSummary orientation="vertical" gap={5}>
          <MigrationDetails />
          <p>
            This process can take several minutes until it completes. You can
            observe progress of this in the operations panel.
          </p>
          <p>
            The elements and sequence flows listed below will be mapped from the
            source on the left side to target on the right side.
          </p>
        </MigrationSummary>
      }
      lowContrast
      hideCloseButton
    />
  );
};

export {MigrationSummaryNotification};
