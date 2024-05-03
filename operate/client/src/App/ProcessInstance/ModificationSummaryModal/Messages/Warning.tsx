/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text, InlineNotification} from './styled';

const Warning: React.FC = () => {
  return (
    <InlineNotification
      kind="warning"
      hideCloseButton
      lowContrast
      title=""
      children={
        <Text>
          The planned modifications will cancel all remaining running flow node
          instances. Applying these modifications will cancel the entire process
          instance.
        </Text>
      }
    />
  );
};

export {Warning};
