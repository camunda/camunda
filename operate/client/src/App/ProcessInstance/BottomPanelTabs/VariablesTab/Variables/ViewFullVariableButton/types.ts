/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

interface MaximizeButtonProps {
  onClick: () => void;
}

interface ViewFullVariableBaseProps {
  variableName: string;
  shouldSubmitOnApply?: boolean;
}

interface ViewFullVariableButtonAddProps extends ViewFullVariableBaseProps {
  mode: 'add';
  scopeId: string | null;
}

interface ViewFullVariableButtonEditProps extends ViewFullVariableBaseProps {
  mode: 'edit';
  variableValue: string;
  variableKey: string;
}

interface ViewFullVariableButtonShowProps extends ViewFullVariableBaseProps {
  mode: 'show';
  variableValue: string;
  variableKey: string;
  buttonLabel?: string;
  canEdit?: boolean;
}

type ViewFullVariableWrapperProps =
  | ViewFullVariableButtonAddProps
  | ViewFullVariableButtonEditProps
  | ViewFullVariableButtonShowProps;

export type {
  MaximizeButtonProps,
  ViewFullVariableButtonAddProps,
  ViewFullVariableButtonEditProps,
  ViewFullVariableButtonShowProps,
  ViewFullVariableWrapperProps,
};
