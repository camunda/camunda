/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Text} from './styled';
import {WarningMessage} from 'modules/components/Messages/WarningMessage';

const Warning: React.FC = () => {
  return (
    <WarningMessage
      content={
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
