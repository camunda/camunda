/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor, within} from '@testing-library/react';
import {UserEvent} from 'modules/testing-library';

const selectComboBoxOption = async ({
  user,
  fieldName,
  option,
  listBoxLabel,
}: {
  user: UserEvent;
  fieldName: string;
  option: string;
  listBoxLabel: string;
}) => {
  await user.click(screen.getByRole('combobox', {name: fieldName}));
  await user.selectOptions(screen.getByRole('listbox', {name: listBoxLabel}), [
    screen.getByRole('option', {name: option}),
  ]);
};

type SelectProps = {user: UserEvent; option: string};

const selectProcess = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Name',
    listBoxLabel: 'Select a Process',
  });
};

const selectProcessVersion = async ({user, option}: SelectProps) => {
  await user.click(screen.getByLabelText('Version', {selector: 'button'}));
  await user.selectOptions(
    within(screen.getByLabelText('Select a Process Version')).getByRole(
      'listbox',
    ),
    [screen.getByRole('option', {name: option})],
  );
};

const selectTenant = async ({user, option}: SelectProps) => {
  await user.click(screen.getByRole('combobox', {name: /Select a tenant/i}));
  await user.click(screen.getByRole('option', {name: option}));
};

const selectFlowNode = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Flow Node',
    listBoxLabel: 'Select a Flow Node',
  });
};

const selectDecision = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Name',
    listBoxLabel: 'Name',
  });
};

const selectDecisionVersion = async ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Select a Decision Version',
    listBoxLabel: 'Version',
  });

  // await user.click(
  //   screen.getByRole('combobox', {name: /Select a decision version/i}),
  // );

  // await user.selectOptions(screen.getByRole('listbox', {name: 'Version'}), [
  //   screen.getByRole('option', {name: option}),
  // ]);
};

const selectTargetProcess = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Target Process',
    listBoxLabel: 'Target Process',
  });
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
  selectTargetProcess,
  clearComboBox,
};
