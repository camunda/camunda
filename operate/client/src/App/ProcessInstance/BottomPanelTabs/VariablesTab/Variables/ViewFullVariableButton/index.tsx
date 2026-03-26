/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ViewFullVariableButtonEdit} from './Edit';
import {ViewFullVariableButtonShow} from './Show';
import {ViewFullVariableButtonAdd} from './Add';
import type {ViewFullVariableWrapperProps} from './types';

const ViewFullVariableButton: React.FC<ViewFullVariableWrapperProps> = (
  props,
) => {
  if (props.mode === 'add') {
    return (
      <ViewFullVariableButtonAdd
        mode="add"
        scopeId={props.scopeId}
        variableName={props.variableName}
      />
    );
  }

  if (props.mode === 'edit') {
    return <ViewFullVariableButtonEdit {...props} />;
  }

  return <ViewFullVariableButtonShow {...props} />;
};

export {ViewFullVariableButton};
