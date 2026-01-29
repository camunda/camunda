/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Accordion} from '@carbon/react';
import type {AgentTimelineItem} from 'modules/agentContext/types';
import {TimelineItem} from './TimelineItem';
import {SectionTitle, TimelineList} from './styled';

type Props = {
  items: AgentTimelineItem[];
};

const HeaderSection: React.FC<Props> = ({items}) => {
  if (items.length === 0) {
    return null;
  }

  return (
    <>
      <SectionTitle>Context</SectionTitle>
      <Accordion align="start">
        <TimelineList>
          {items.map((item) => (
            <TimelineItem key={item.id} item={item} isHeader />
          ))}
        </TimelineList>
      </Accordion>
    </>
  );
};

export {HeaderSection};
