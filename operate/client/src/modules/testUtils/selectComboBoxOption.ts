/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor, within} from '@testing-library/react';
import type {UserEvent} from 'modules/testing-library';

const selectComboBoxOption = async ({
  user,
  fieldName,
  option,
}: {
  user: UserEvent;
  fieldName: string;
  option: string;
}) => {
  await user.click(screen.getByRole('combobox', {name: fieldName}));
  await user.click(screen.getByRole('option', {name: option}));
};

type SelectProps = {user: UserEvent; option: string};

const selectProcess = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Name',
  });
};

const selectProcessVersion = async ({user, option}: SelectProps) => {
  await user.click(
    screen.getByRole('combobox', {name: 'Select a Process Version'}),
  );
  await user.click(screen.getByRole('option', {name: option}));
};

const selectTenant = async ({user, option}: SelectProps) => {
  await user.click(screen.getByRole('combobox', {name: 'Select a tenant'}));
  await user.click(screen.getByRole('option', {name: option}));
};

const selectFlowNode = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Element',
  });
};

const selectDecision = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Name',
  });
};

const selectDecisionVersion = async ({user, option}: SelectProps) => {
  await user.click(screen.getByLabelText('Version', {selector: 'button'}));
  await user.click(screen.getByRole('option', {name: option}));
};

const clearComboBox = async ({
  user,
  fieldName,
}: {
  user: UserEvent;
  fieldName: string;
}) => {
  const parentElement = screen.getByRole('combobox', {
    name: fieldName,
  }).parentElement;

  await waitFor(() => expect(parentElement).toBeInTheDocument());

  if (parentElement) {
    return user.click(
      within(parentElement).getByRole('button', {
        name: 'Clear selected item',
      }),
    );
  }
};

export {
  selectProcess,
  selectProcessVersion,
  selectTenant,
  selectFlowNode,
  selectDecision,
  selectDecisionVersion,
  clearComboBox,
};
