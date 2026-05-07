/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import * as React from 'react';
import {Button} from '../button/button.shadcn';
import {Tile as AdapterTile} from './tile.adapter';
import {Tile as CarbonTile} from './tile.carbon';
import {
  Card,
  CardAction,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from './tile.shadcn';

const meta: Meta = {
  title: 'UI/Tile',
};
export default meta;

type Story = StoryObj;

export const Basic: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTile className="max-w-sm p-6">
          <h3 className="font-medium">Default tile</h3>
          <p className="text-sm mt-2">
            A simple Carbon tile. Container only — no built-in title /
            description structure.
          </p>
        </CarbonTile>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (Card)</div>
        <Card className="max-w-sm">
          <CardHeader>
            <CardTitle>Default card</CardTitle>
            <CardDescription>
              A simple shadcn card with first-class title + description slots.
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTile className="max-w-sm p-6">
          <h3 className="font-medium">Default tile</h3>
          <p className="text-sm mt-2">
            A simple Carbon tile. Container only — no built-in title /
            description structure.
          </p>
        </AdapterTile>
      </div>
    </div>
  ),
};

export const WithFooter: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTile className="max-w-sm p-6">
          <h3 className="font-medium">Project Atlas</h3>
          <p className="text-sm mt-2 mb-4">
            Manage your project's deployment pipeline.
          </p>
          <div className="flex justify-end gap-2">
            <button type="button" className="text-sm underline">
              Cancel
            </button>
            <button type="button" className="text-sm font-medium underline">
              Open
            </button>
          </div>
        </CarbonTile>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Card className="max-w-sm">
          <CardHeader>
            <CardTitle>Project Atlas</CardTitle>
            <CardDescription>
              Manage your project's deployment pipeline.
            </CardDescription>
          </CardHeader>
          <CardFooter className="justify-end gap-2">
            <Button variant="outline">Cancel</Button>
            <Button>Open</Button>
          </CardFooter>
        </Card>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTile className="max-w-sm p-6">
          <h3 className="font-medium">Project Atlas</h3>
          <p className="text-sm mt-2 mb-4">
            Manage your project's deployment pipeline.
          </p>
          <div className="flex justify-end gap-2">
            <button type="button" className="text-sm underline">
              Cancel
            </button>
            <button type="button" className="text-sm font-medium underline">
              Open
            </button>
          </div>
        </AdapterTile>
      </div>
    </div>
  ),
};

export const WithAction: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (manual flex layout)
        </div>
        <CarbonTile className="max-w-sm p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="font-medium">Notifications</h3>
              <p className="text-sm mt-1">12 unread items</p>
            </div>
            <Button variant="outline" size="sm">
              Mark all read
            </Button>
          </div>
        </CarbonTile>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (<code>CardAction</code> slot)
        </div>
        <Card className="max-w-sm">
          <CardHeader>
            <CardTitle>Notifications</CardTitle>
            <CardDescription>12 unread items</CardDescription>
            <CardAction>
              <Button variant="outline" size="sm">
                Mark all read
              </Button>
            </CardAction>
          </CardHeader>
        </Card>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTile className="max-w-sm p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="font-medium">Notifications</h3>
              <p className="text-sm mt-1">12 unread items</p>
            </div>
            <Button variant="outline" size="sm">
              Mark all read
            </Button>
          </div>
        </AdapterTile>
      </div>
    </div>
  ),
};

export const Clickable: Story = {
  render: () => {
    const Carbon = () => {
      const [count, setCount] = React.useState(0);
      return (
        <CarbonTile
          className="max-w-sm p-6 cursor-pointer hover:bg-muted/50"
          onClick={() => setCount(count + 1)}
        >
          <h3 className="font-medium">Click count: {count}</h3>
          <p className="text-sm mt-2">Click anywhere on this tile.</p>
        </CarbonTile>
      );
    };
    const Shadcn = () => {
      const [count, setCount] = React.useState(0);
      return (
        <Card
          role="button"
          tabIndex={0}
          className="max-w-sm cursor-pointer transition-colors hover:bg-muted/50"
          onClick={() => setCount(count + 1)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              setCount(count + 1);
            }
          }}
        >
          <CardHeader>
            <CardTitle>Click count: {count}</CardTitle>
            <CardDescription>Click anywhere on this card.</CardDescription>
          </CardHeader>
        </Card>
      );
    };
    const Adapter = () => {
      const [count, setCount] = React.useState(0);
      return (
        <AdapterTile
          className="max-w-sm p-6 cursor-pointer hover:bg-muted/50"
          onClick={() => setCount(count + 1)}
        >
          <h3 className="font-medium">Click count: {count}</h3>
          <p className="text-sm mt-2">Click anywhere on this tile.</p>
        </AdapterTile>
      );
    };
    return (
      <div className="grid grid-cols-3 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            Carbon (<code>ClickableTile</code> — separate component)
          </div>
          <Carbon />
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn (manual <code>onClick</code> + a11y wiring)
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
