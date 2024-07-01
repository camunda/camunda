/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';

import {getFlowNodeNames} from 'services';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

export function FlowNodeResolver({definition, render, mightFail}) {
  const [flowNodeNames, setFlowNodeNames] = useState(null);

  useEffect(() => {
    mightFail(
      getFlowNodeNames(definition.key, definition.versions?.[0], definition.tenantIds?.[0]),
      setFlowNodeNames,
      showError
    );
  }, [definition, mightFail]);

  return render(flowNodeNames);
}

export default withErrorHandling(FlowNodeResolver);
