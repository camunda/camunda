/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState, useEffect} from 'react';

import {getFlowNodeNames} from 'services';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

export function FlowNodeResolver({definition, render, mightFail}) {
  const [flowNodeNames, setFlowNodeNames] = useState({});

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
