/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {CodeSnippet as CarbonCodeSnippet} from '@carbon/react';
import {CodeSnippet as AdapterCodeSnippet} from './code-snippet.adapter';
import {CodeSnippet} from './code-snippet.shadcn';

const meta: Meta = {
  title: 'UI/CodeSnippet',
};
export default meta;

type Story = StoryObj;

const single = `npm install @camunda/design-system`;
const multi = `import {Button} from '@camunda/design-system';

function Example() {
  return <Button>Click me</Button>;
}

// You can also wrap any other element with asChild:
<Button asChild>
  <a href="/docs">Read the docs</a>
</Button>`;

export const Inline: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <p className="text-sm">
          Run <CarbonCodeSnippet type="inline">npm install</CarbonCodeSnippet> first.
        </p>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <p className="text-sm">
          Run <CodeSnippet type="inline">npm install</CodeSnippet> first.
        </p>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <p className="text-sm">
          Run <AdapterCodeSnippet type="inline">npm install</AdapterCodeSnippet>{' '}
          first.
        </p>
      </div>
    </div>
  ),
};

export const Single: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCodeSnippet type="single">{single}</CarbonCodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <CodeSnippet type="single">{single}</CodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCodeSnippet type="single">{single}</AdapterCodeSnippet>
      </div>
    </div>
  ),
};

export const Multi: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCodeSnippet type="multi">{multi}</CarbonCodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <CodeSnippet type="multi">{multi}</CodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCodeSnippet type="multi">{multi}</AdapterCodeSnippet>
      </div>
    </div>
  ),
};

export const HideCopyButton: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (hideCopyButton)</div>
        <CarbonCodeSnippet type="single" hideCopyButton>
          {single}
        </CarbonCodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (hideCopyButton)</div>
        <CodeSnippet type="single" hideCopyButton>
          {single}
        </CodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API)
        </div>
        <AdapterCodeSnippet type="single" hideCopyButton>
          {single}
        </AdapterCodeSnippet>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (disabled)</div>
        <CarbonCodeSnippet type="single" disabled>
          {single}
        </CarbonCodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (disabled)</div>
        <CodeSnippet type="single" disabled>
          {single}
        </CodeSnippet>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCodeSnippet type="single" disabled>
          {single}
        </AdapterCodeSnippet>
      </div>
    </div>
  ),
};
