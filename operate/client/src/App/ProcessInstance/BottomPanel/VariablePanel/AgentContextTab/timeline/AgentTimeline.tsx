/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Accordion} from '@carbon/react';
import type {AgentTimelineModel} from 'modules/agentContext/types';
import {TimelineItem} from './TimelineItem';
import {Container, SectionTitle, TimelineList} from './styled';

type Props = {
  model: AgentTimelineModel;
};

const AgentTimeline: React.FC<Props> = ({model}) => {
  return (
    <Container>
      <SectionTitle>Timeline</SectionTitle>
      <Accordion align="start">
        <TimelineList>
          {model.events.map((event, idx) => (
            <TimelineItem
              key={event.id}
              item={event}
              isFirst={idx === 0}
              isLast={idx === model.events.length - 1}
            />
          ))}
        </TimelineList>
      </Accordion>
    </Container>
  );
};

export {AgentTimeline};
