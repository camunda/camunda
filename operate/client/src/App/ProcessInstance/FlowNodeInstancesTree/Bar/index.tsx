/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import {TimeStampLabel} from './TimeStampLabel';
import {NodeName, Container, StateIcon} from './styled';
import {Layer, Stack, Tag} from '@carbon/react';
import {ModificationIcons} from './ModificationIcons';
import {ModificationIcons as ModificationIconsV2} from './ModificationIcons/v2';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {formatDate} from 'modules/utils/date';
import {IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED} from 'modules/feature-flags';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  nodeName: string;
  isTimestampLabelVisible?: boolean;
  isRoot?: boolean;
  latestMigrationDate?: string;
};

const Bar = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      nodeName,
      flowNodeInstance,
      isTimestampLabelVisible = false,
      isRoot = false,
      latestMigrationDate,
    },
    ref,
  ) => {
    return (
      <Container ref={ref} data-testid={`node-details-${flowNodeInstance.id}`}>
        <Stack orientation="horizontal" gap={5}>
          {flowNodeInstance.state !== undefined && (
            <StateIcon state={flowNodeInstance.state} size={16} />
          )}
          <NodeName>{nodeName}</NodeName>
          {isRoot && latestMigrationDate !== undefined && (
            <Tag type="green">{`Migrated ${formatDate(latestMigrationDate)}`}</Tag>
          )}
          {isTimestampLabelVisible && (
            <Layer>
              <TimeStampLabel timeStamp={flowNodeInstance.endDate} />
            </Layer>
          )}
        </Stack>
        {IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED ? (
          <ModificationIconsV2 flowNodeInstance={flowNodeInstance} />
        ) : (
          <ModificationIcons flowNodeInstance={flowNodeInstance} />
        )}
      </Container>
    );
  },
);

export {Bar};
