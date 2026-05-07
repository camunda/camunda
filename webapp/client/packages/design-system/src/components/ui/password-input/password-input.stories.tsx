/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {PasswordInput as CarbonPasswordInput} from './password-input.carbon';
import {PasswordInput as AdapterPasswordInput} from './password-input.adapter';
import {PasswordInput} from './password-input.shadcn';

const meta: Meta = {title: 'UI/PasswordInput'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8 w-96">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonPasswordInput
          id="carbon-password"
          labelText="Password"
          placeholder="Enter your password"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <PasswordInput
          id="shadcn-password"
          placeholder="Enter your password"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterPasswordInput
          id="adapter-password"
          labelText="Password"
          placeholder="Enter your password"
          helperText="At least 12 characters"
        />
      </div>
    </div>
  ),
};

export const Invalid: Story = {
  render: () => (
    <div className="w-96 pt-8">
      <AdapterPasswordInput
        id="invalid-password"
        labelText="Password"
        placeholder="Enter your password"
        invalid
        invalidText="Password must contain at least one uppercase letter"
      />
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="w-96 pt-8">
      <AdapterPasswordInput
        id="disabled-password"
        labelText="Password"
        placeholder="Enter your password"
        disabled
      />
    </div>
  ),
};
