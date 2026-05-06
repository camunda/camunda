/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {TextArea as CarbonTextArea} from './text-area.carbon';
import {Textarea} from './text-area.shadcn';

const meta: Meta = {
  title: 'UI/TextArea',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextArea
          id="ta-carbon-default"
          labelText="Description"
          placeholder="Describe what happened…"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Description</label>
          <Textarea placeholder="Describe what happened…" />
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
        <CarbonTextArea
          id="ta-carbon-helper"
          labelText="Bio"
          helperText="Markdown is supported."
          placeholder="Tell us about yourself"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Bio</label>
          <Textarea placeholder="Tell us about yourself" />
          <p className="text-xs text-muted-foreground">
            Markdown is supported.
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
        <CarbonTextArea
          id="ta-carbon-invalid"
          labelText="Reason"
          invalid
          invalidText="A reason is required."
          placeholder="Why are you cancelling?"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Reason</label>
          <Textarea
            placeholder="Why are you cancelling?"
            aria-invalid
            className="border-destructive focus-visible:ring-destructive"
          />
          <p className="text-xs text-destructive">A reason is required.</p>
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
        <CarbonTextArea
          id="ta-carbon-disabled"
          labelText="Notes"
          disabled
          defaultValue="Read-only content"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Notes</label>
          <Textarea disabled defaultValue="Read-only content" />
        </div>
      </div>
    </div>
  ),
};

export const FixedRows: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTextArea
          id="ta-carbon-rows"
          labelText="Comment"
          rows={6}
          placeholder="Write a comment"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Comment</label>
          <Textarea rows={6} placeholder="Write a comment" />
        </div>
      </div>
    </div>
  ),
};
