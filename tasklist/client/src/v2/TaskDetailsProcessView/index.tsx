/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useOutletContext} from 'react-router-dom';
import {ProcessDiagramView} from 'common/tasks/details/ProcessDiagramView';
import type {OutletContext} from 'v2/TaskDetailsLayout';

const TaskDetailsProcessView: React.FC = () => {
  const {task, processXml} = useOutletContext<OutletContext>();
  const processName = task.processName ?? task.processDefinitionId;

  return (
    <ProcessDiagramView
      status={processXml === undefined ? 'error' : 'diagram'}
      xml={processXml ?? ''}
      elementId={task.elementId}
      processName={processName}
      processVersion={task.processDefinitionVersion}
    />
  );
};

export {TaskDetailsProcessView as Component};
