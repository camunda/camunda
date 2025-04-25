/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useOutletContext} from 'react-router-dom';
import {ProcessDiagramView} from 'common/tasks/details/ProcessDiagramView';
import type {OutletContext} from 'v1/TaskDetailsLayout';

const TaskDetailsProcessView: React.FC = () => {
  const {task, process} = useOutletContext<OutletContext>();
  const {taskDefinitionId} = task;

  return (
    <ProcessDiagramView
      status={process === undefined ? 'error' : 'diagram'}
      xml={process?.bpmnXml ?? ''}
      elementId={taskDefinitionId}
      processName={process?.name ?? ''}
      processVersion={process?.version ?? 0}
    />
  );
};

export {TaskDetailsProcessView as Component};
