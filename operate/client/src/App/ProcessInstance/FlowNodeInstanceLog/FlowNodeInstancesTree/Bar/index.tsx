/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef} from 'react';
import {TimeStampLabel} from './TimeStampLabel';
import {NodeName, Container, StateIcon} from './styled';
import {Layer, Stack, Tag} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {ModificationIcons} from './ModificationIcons';

type Props = {
  elementInstanceKey: string;
  elementId: string;
  elementName: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate?: string;
  isTimestampLabelVisible: boolean;
  isRoot: boolean;
  latestMigrationDate: string | undefined;
  scopeKeyHierarchy: string[];
};

const Bar = forwardRef<HTMLDivElement, Props>(
  (
    {
      elementInstanceKey,
      elementId,
      elementName,
      elementInstanceState,
      hasIncident,
      endDate,
      isTimestampLabelVisible,
      isRoot,
      latestMigrationDate,
      scopeKeyHierarchy,
    },
    ref,
  ) => {
    return (
      <Container ref={ref} data-testid={`node-details-${elementInstanceKey}`}>
        <Stack orientation="horizontal" gap={5}>
          {hasIncident ? (
            <StateIcon state="INCIDENT" size={16} />
          ) : (
            <StateIcon state={elementInstanceState} size={16} />
          )}
          <NodeName>{elementName}</NodeName>
          {isRoot && latestMigrationDate !== undefined && (
            <Tag type="green">{`Migrated ${formatDate(latestMigrationDate)}`}</Tag>
          )}
          {isTimestampLabelVisible && endDate && (
            <Layer>
              <TimeStampLabel timeStamp={endDate} />
            </Layer>
          )}
        </Stack>
        <ModificationIcons
          elementId={elementId}
          isPlaceholder={false}
          endDate={endDate}
          scopeKeyHierarchy={scopeKeyHierarchy}
        />
      </Container>
    );
  },
);

export {Bar};
