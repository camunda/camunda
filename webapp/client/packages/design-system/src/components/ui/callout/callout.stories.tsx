/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {AlertTriangle, Info} from 'lucide-react';
import {Button} from '../button/button.shadcn';
import {Callout as AdapterCallout} from './callout.adapter';
import {Callout as CarbonCallout} from './callout.carbon';
import {Alert, AlertDescription, AlertTitle} from './callout.shadcn';

const meta: Meta = {
  title: 'UI/Callout',
};
export default meta;

type Story = StoryObj;

export const Info_: Story = {
  name: 'Info',
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCallout
          kind="info"
          title="Heads up"
          subtitle="This action will affect 12 records."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Alert>
          <Info className="h-4 w-4" />
          <AlertTitle>Heads up</AlertTitle>
          <AlertDescription>
            This action will affect 12 records.
          </AlertDescription>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCallout
          kind="info"
          title="Heads up"
          subtitle="This action will affect 12 records."
        />
      </div>
    </div>
  ),
};

export const Warning: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCallout
          kind="warning"
          title="Read carefully"
          subtitle="Some changes here cannot be undone."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (no warning variant — restyle via className)
        </div>
        <Alert className="border-yellow-500/50 text-yellow-700 dark:border-yellow-500 [&>svg]:text-yellow-600">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Read carefully</AlertTitle>
          <AlertDescription>
            Some changes here cannot be undone.
          </AlertDescription>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCallout
          kind="warning"
          title="Read carefully"
          subtitle="Some changes here cannot be undone."
        />
      </div>
    </div>
  ),
};

export const WithActionButton: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCallout
          kind="info"
          title="Quota reached"
          subtitle="You've used 90% of your monthly quota."
          actionButtonLabel="Upgrade plan"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Alert + Button)
        </div>
        <Alert className="flex items-start gap-4">
          <Info className="h-4 w-4" />
          <div className="flex-1">
            <AlertTitle>Quota reached</AlertTitle>
            <AlertDescription>
              You've used 90% of your monthly quota.
            </AlertDescription>
          </div>
          <Button size="sm" variant="outline" className="shrink-0">
            Upgrade plan
          </Button>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCallout
          kind="info"
          title="Quota reached"
          subtitle="You've used 90% of your monthly quota."
          actionButtonLabel="Upgrade plan"
        />
      </div>
    </div>
  ),
};

export const LowContrast: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCallout
          kind="info"
          title="Heads up"
          subtitle="Low-contrast variant is more subtle."
          lowContrast
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (no lowContrast prop — drop border / lighten via className)
        </div>
        <Alert className="border-transparent bg-muted">
          <Info className="h-4 w-4" />
          <AlertTitle>Heads up</AlertTitle>
          <AlertDescription>
            Low-contrast variant is more subtle.
          </AlertDescription>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCallout
          kind="info"
          title="Heads up"
          subtitle="Low-contrast variant is more subtle."
          lowContrast
        />
      </div>
    </div>
  ),
};
