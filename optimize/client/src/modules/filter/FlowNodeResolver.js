/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
