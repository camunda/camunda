/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TaskPanelItemSkeleton} from './TaskPanelItemSkeleton';

type Props = {
  className?: string;
};

const AvailableTasksSkeleton: React.FC<Props> = ({className}) => {
  return (
    <div data-testid="tasks-skeleton" className={className}>
      {Array.from({length: 50}).map((_, index) => (
        <TaskPanelItemSkeleton key={index} />
      ))}
    </div>
  );
};

export {AvailableTasksSkeleton};
