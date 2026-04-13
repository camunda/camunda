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
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {ModificationIcons} from './ModificationIcons';
import {useAgentData} from 'modules/contexts/agentData';

type Props = {
  elementInstanceKey: string;
  elementId: string;
  elementName: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate: string | null;
  isTimestampLabelVisible: boolean;
  isRoot: boolean;
  latestMigrationDate: string | null;
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
    const {getIterationSummary} = useAgentData();
    const iterationSummary = getIterationSummary(elementId);

    return (
      <Container ref={ref} data-testid={`node-details-${elementInstanceKey}`}>
        <Stack orientation="horizontal" gap={5}>
          {hasIncident ? (
            <StateIcon state="INCIDENT" size={16} />
          ) : (
            <StateIcon state={elementInstanceState} size={16} />
          )}
          {iterationSummary ? (
            <div data-multiline style={{display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1, marginLeft: 'var(--cds-spacing-02)', padding: 'var(--cds-spacing-02) 0'}}>
              <span>{elementName}</span>
              <span
                style={{
                  fontSize: 11,
                  color: 'var(--cds-text-secondary)',
                  lineHeight: '1.4',
                  display: '-webkit-box',
                  WebkitLineClamp: 10,
                  WebkitBoxOrient: 'vertical' as const,
                  overflow: 'hidden',
                }}
              >
                {iterationSummary}
              </span>
            </div>
          ) : (
            <NodeName>{elementName}</NodeName>
          )}
          {isRoot && latestMigrationDate !== null && (
            <Tag type="green">{`Migrated ${formatDate(latestMigrationDate)}`}</Tag>
          )}
          {isTimestampLabelVisible && endDate !== null && (
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
