/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text, InlineNotification} from './styled';

const OrphanedVariablesError: React.FC = () => {
  return (
    <InlineNotification
      kind="error"
      hideCloseButton
      lowContrast
      title="Invalid modification plan"
      children={
        <Text>
          Some planned variable modifications cannot be applied. They must be
          scoped to elements with a pending ADD or MOVE token modification. For
          global variable modifications (scoped to the process instance), at
          least one ADD or MOVE token modification must be planned.
        </Text>
      }
    />
  );
};

export {OrphanedVariablesError};
