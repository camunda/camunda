/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {TextInput as CarbonTextInput} from './text-input.carbon';
import {Input} from './text-input.shadcn';

const meta: Meta = {
  title: 'UI/TextInput',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextInput
          id="ti-carbon-default"
          labelText="Email"
          placeholder="you@example.com"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Email</label>
          <Input type="email" placeholder="you@example.com" />
        </div>
      </div>
    </div>
  ),
};

export const WithHelperText: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextInput
          id="ti-carbon-helper"
          labelText="Username"
          helperText="Letters, numbers, hyphens. 3-20 chars."
          placeholder="alice42"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Username</label>
          <Input placeholder="alice42" />
          <p className="text-xs text-muted-foreground">
            Letters, numbers, hyphens. 3-20 chars.
          </p>
        </div>
      </div>
    </div>
  ),
};

export const Invalid: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextInput
          id="ti-carbon-invalid"
          labelText="Email"
          invalid
          invalidText="Enter a valid email address"
          placeholder="you@example.com"
          defaultValue="not-an-email"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Email</label>
          <Input
            type="email"
            placeholder="you@example.com"
            defaultValue="not-an-email"
            aria-invalid
          />
          <p className="text-xs text-destructive">
            Enter a valid email address
          </p>
        </div>
      </div>
    </div>
  ),
};

export const Password: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>PasswordInput</code> — separate component)
        </div>
        <CarbonTextInput
          id="ti-carbon-password"
          labelText="Password"
          type="password"
          placeholder="••••••••"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (<code>type="password"</code>)
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Password</label>
          <Input type="password" placeholder="••••••••" />
        </div>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextInput
          id="ti-carbon-disabled"
          labelText="ID"
          disabled
          defaultValue="user-12345"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">ID</label>
          <Input disabled defaultValue="user-12345" />
        </div>
      </div>
    </div>
  ),
};

export const ReadOnly: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextInput
          id="ti-carbon-readonly"
          labelText="Generated key"
          readOnly
          defaultValue="abc-def-ghi-jkl"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Generated key</label>
          <Input readOnly defaultValue="abc-def-ghi-jkl" />
        </div>
      </div>
    </div>
  ),
};
