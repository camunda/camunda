/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Time} from '@carbon/react/icons';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.10';
import {getWaitStateStatusItems} from 'modules/utils/waitStates';
import {StatusContainer, StatusHeading, StatusItem} from './styled';

type Props = {
  waitStates: ElementInstanceInspection[];
};

const WaitingStatus: React.FC<Props> = ({waitStates}) => {
  if (waitStates.length === 0) {
    return null;
  }

  const statusItems = getWaitStateStatusItems(waitStates);

  return (
    <StatusContainer data-testid="waiting-status">
      <StatusHeading>Status</StatusHeading>
      {statusItems.map((item, index) => (
        <StatusItem
          key={`${waitStates[index]!.elementInstanceKey}-${waitStates[index]!.waitStateType}`}
        >
          <Time size={16} />
          <span>{item.text}</span>
        </StatusItem>
      ))}
    </StatusContainer>
  );
};

export {WaitingStatus};
