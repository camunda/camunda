/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ComboBox} from '.';

const items = [
  {
    id: 'process1',
    label: 'Process One',
  },
  {
    id: 'process2',
    label: 'Process Two',
  },
  {
    id: 'process3',
    label: 'Process Three',
  },
];

describe('<ComboBox />', () => {
  it('should be disabled when there are no items', () => {
    render(
      <ComboBox
        id="process-name"
        titleText="Name"
        items={[]}
        value=""
        onChange={() => {}}
      />,
    );

    expect(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();
  });

  it('should initially select item', () => {
    render(
      <ComboBox
        id="process-name"
        titleText="Name"
        items={items}
        value="process2"
        onChange={() => {}}
      />,
    );

    expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
      'Process Two',
    );
  });

  it('should filter items', async () => {
    const {user} = render(
      <ComboBox
        id="process-name"
        titleText="Name"
        items={items}
        value=""
        onChange={() => {}}
      />,
    );

    await user.type(screen.getByRole('combobox', {name: 'Name'}), 'ONE');
    expect(screen.getAllByRole('option')).toHaveLength(1);
    expect(screen.getAllByRole('option')[0]?.textContent).toEqual(
      'Process One',
    );

    await user.click(screen.getByRole('button', {name: 'Clear selected item'}));

    await user.type(screen.getByRole('combobox', {name: 'Name'}), 'process t');
    expect(screen.getAllByRole('option')).toHaveLength(2);
    expect(screen.getAllByRole('option')[0]?.textContent).toEqual(
      'Process Two',
    );
    expect(screen.getAllByRole('option')[1]?.textContent).toEqual(
      'Process Three',
    );

    await user.click(screen.getByRole('button', {name: 'Clear selected item'}));

    await user.type(screen.getByRole('combobox', {name: 'Name'}), 'unknown');
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
  });
});
