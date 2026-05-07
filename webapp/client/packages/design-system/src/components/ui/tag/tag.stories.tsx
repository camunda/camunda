/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {X} from 'lucide-react';
import * as React from 'react';
import {Tag as AdapterTag} from './tag.adapter';
import {Tag as CarbonTag} from './tag.carbon';
import {Badge} from './tag.shadcn';

const meta: Meta = {
  title: 'UI/Tag',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTag>Default</CarbonTag>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Badge>Default</Badge>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTag>Default</AdapterTag>
      </div>
    </div>
  ),
};

export const Variants: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (type)</div>
        <div className="flex flex-wrap gap-2">
          <CarbonTag type="red">Red</CarbonTag>
          <CarbonTag type="magenta">Magenta</CarbonTag>
          <CarbonTag type="purple">Purple</CarbonTag>
          <CarbonTag type="blue">Blue</CarbonTag>
          <CarbonTag type="cyan">Cyan</CarbonTag>
          <CarbonTag type="teal">Teal</CarbonTag>
          <CarbonTag type="green">Green</CarbonTag>
          <CarbonTag type="gray">Gray</CarbonTag>
          <CarbonTag type="cool-gray">Cool gray</CarbonTag>
          <CarbonTag type="warm-gray">Warm gray</CarbonTag>
          <CarbonTag type="high-contrast">High contrast</CarbonTag>
          <CarbonTag type="outline">Outline</CarbonTag>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (variant)</div>
        <div className="flex flex-wrap gap-2">
          <Badge>Default</Badge>
          <Badge variant="secondary">Secondary</Badge>
          <Badge variant="destructive">Destructive</Badge>
          <Badge variant="outline">Outline</Badge>
          <Badge variant="ghost">Ghost</Badge>
          <Badge variant="link">Link</Badge>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex flex-wrap gap-2">
          <AdapterTag type="red">Red</AdapterTag>
          <AdapterTag type="magenta">Magenta</AdapterTag>
          <AdapterTag type="purple">Purple</AdapterTag>
          <AdapterTag type="blue">Blue</AdapterTag>
          <AdapterTag type="cyan">Cyan</AdapterTag>
          <AdapterTag type="teal">Teal</AdapterTag>
          <AdapterTag type="green">Green</AdapterTag>
          <AdapterTag type="gray">Gray</AdapterTag>
          <AdapterTag type="cool-gray">Cool gray</AdapterTag>
          <AdapterTag type="warm-gray">Warm gray</AdapterTag>
          <AdapterTag type="high-contrast">High contrast</AdapterTag>
          <AdapterTag type="outline">Outline</AdapterTag>
        </div>
      </div>
    </div>
  ),
};

export const Filter: Story = {
  render: () => {
    const Carbon = () => {
      const [shown, setShown] = React.useState(true);
      if (!shown) return <span className="text-xs italic">(removed)</span>;
      return (
        <CarbonTag
          filter
          type="blue"
          onClose={() => setShown(false)}
          title="Remove"
        >
          Frontend
        </CarbonTag>
      );
    };
    const Shadcn = () => {
      const [shown, setShown] = React.useState(true);
      if (!shown) return <span className="text-xs italic">(removed)</span>;
      return (
        <Badge variant="secondary" className="gap-1 pr-1">
          Frontend
          <button
            type="button"
            aria-label="Remove"
            className="ml-1 rounded-full p-0.5 hover:bg-foreground/10"
            onClick={() => setShown(false)}
          >
            <X className="size-3" />
          </button>
        </Badge>
      );
    };
    const Adapter = () => {
      const [shown, setShown] = React.useState(true);
      if (!shown) return <span className="text-xs italic">(removed)</span>;
      return (
        <AdapterTag
          filter
          type="blue"
          onClose={() => setShown(false)}
          title="Remove"
        >
          Frontend
        </AdapterTag>
      );
    };
    return (
      <div className="grid grid-cols-3 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            Carbon (<code>filter</code> + <code>onClose</code>)
          </div>
          <Carbon />
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn (compose Badge + close button)
          </div>
          <Shadcn />
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">
            Adapter (Carbon API)
          </div>
          <Adapter />
        </div>
      </div>
    );
  },
};

export const WithIcon: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (renderIcon)</div>
        <CarbonTag type="green">Active</CarbonTag>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (icon as child, sized via badge svg utilities)
        </div>
        <Badge>
          <span
            aria-hidden
            className="block h-2 w-2 rounded-full bg-current"
          />
          Active
        </Badge>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTag type="green">Active</AdapterTag>
      </div>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <div className="flex flex-wrap items-center gap-2">
          <CarbonTag size="sm">sm</CarbonTag>
          <CarbonTag size="md">md</CarbonTag>
          <CarbonTag size="lg">lg</CarbonTag>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (one size — restyle via className)
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge className="text-[10px] px-1.5 py-0">sm</Badge>
          <Badge>md (default)</Badge>
          <Badge className="text-sm px-3 py-1">lg</Badge>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <div className="flex flex-wrap items-center gap-2">
          <AdapterTag size="sm">sm</AdapterTag>
          <AdapterTag size="md">md</AdapterTag>
          <AdapterTag size="lg">lg</AdapterTag>
        </div>
      </div>
    </div>
  ),
};
