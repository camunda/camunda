/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CopiableContent} from 'modules/components/PanelHeader/CopiableContent';

type Props = {
  processDefinitionId?: string;
};

const CopiableProcessID: React.FC<Props> = ({processDefinitionId}) => {
  if (processDefinitionId === undefined) {
    return null;
  }

  return (
    <CopiableContent
      copyButtonDescription="Process ID / Click to copy"
      content={processDefinitionId}
    />
  );
};

export {CopiableProcessID};
