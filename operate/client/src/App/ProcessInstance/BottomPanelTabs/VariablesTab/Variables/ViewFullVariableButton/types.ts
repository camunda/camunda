/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export interface MaximizeButtonProps {
  label: string;
  onClick: () => void;
}

export interface ViewFullVariableButtonAddProps {
  mode: 'add';
  variableName: string;
  scopeId: string | null;
}

export interface ViewFullVariableButtonEditProps {
  mode: 'edit';
  variableName: string;
  variableValue: string;
  variableKey: string;
}

export interface ViewFullVariableButtonShowProps {
  mode: 'show';
  variableName: string;
  variableValue: string;
  variableKey: string;
}

export type ViewFullVariableWrapperProps =
  | ViewFullVariableButtonAddProps
  | ViewFullVariableButtonEditProps
  | ViewFullVariableButtonShowProps;
