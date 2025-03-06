/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef, useEffect} from 'react';
import {useFieldArray} from 'react-final-form-arrays';

const OnNewVariableAdded: React.FC<{execute: () => void; name: string}> = ({
  execute,
  name,
}) => {
  const {fields} = useFieldArray(name, {
    subscription: {length: true},
  });
  const currentLength = fields.length ?? 0;
  const lengthRef = useRef(currentLength);

  useEffect(() => {
    if (currentLength > lengthRef.current) {
      execute();
    }

    lengthRef.current = currentLength;
  }, [execute, currentLength]);

  return null;
};

export {OnNewVariableAdded};
