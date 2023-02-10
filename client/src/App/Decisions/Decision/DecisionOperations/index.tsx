/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {OperationItem} from 'modules/components/OperationItem';
import {OperationItems} from 'modules/components/OperationItems';

type Props = {
  decisionName: string;
  decisionVersion: string;
};

const DecisionOperations: React.FC<Props> = ({
  decisionName,
  decisionVersion,
}) => {
  return (
    <OperationItems>
      <OperationItem
        title={`Delete Decision Definition "${decisionName} - Version ${decisionVersion}"`}
        type="DELETE"
      />
    </OperationItems>
  );
};

export {DecisionOperations};
