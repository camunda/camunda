/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen, within} from '@testing-library/react';
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
  await user.click(screen.getByLabelText(fieldName));
  await user.selectOptions(screen.getByRole('listbox', {name: listBoxLabel}), [
    screen.getByRole('option', {name: option}),
  ]);
};

type SelectProps = {user: UserEvent; option: string};

const selectProcess = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Process',
    listBoxLabel: 'Select a Process',
  });
};

const selectProcessVersion = async ({user, option}: SelectProps) => {
  await user.click(screen.getByLabelText('Version', {selector: 'button'}));
  await user.selectOptions(
    within(screen.getByLabelText('Select a Process Version')).getByRole(
      'listbox'
    ),
    [screen.getByRole('option', {name: option})]
  );
};

const selectFlowNode = ({user, option}: SelectProps) => {
  return selectComboBoxOption({
    user,
    option,
    fieldName: 'Flow Node',
    listBoxLabel: 'Select a Flow Node',
  });
};

export {selectProcess, selectProcessVersion, selectFlowNode};
