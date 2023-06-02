/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
