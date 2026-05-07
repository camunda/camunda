/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Button} from '../button/button.shadcn';
import {ToastNotification as AdapterToastNotification} from './toast-notification.adapter';
import {ToastNotification as CarbonToastNotification} from './toast-notification.carbon';
import {Toaster, toast} from './toast-notification.shadcn';

const meta: Meta = {
  title: 'UI/ToastNotification',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (rendered inline; you control mount/unmount)
        </div>
        <CarbonToastNotification
          kind="info"
          title="New version available"
          subtitle="Reload the page to see the latest content."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (imperative <code>toast(...)</code> + global Toaster)
        </div>
        <div className="flex flex-col gap-3">
          <Button onClick={() => toast('Default toast')}>Default</Button>
          <Button
            variant="outline"
            onClick={() =>
              toast.success('Saved', {description: 'Your changes are live.'})
            }
          >
            Success
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              toast.info('Heads up', {description: 'New version available.'})
            }
          >
            Info
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              toast.warning('Read carefully', {
                description: 'Some changes cannot be undone.',
              })
            }
          >
            Warning
          </Button>
          <Button
            variant="destructive"
            onClick={() =>
              toast.error('Could not save', {
                description: 'Please try again.',
              })
            }
          >
            Error
          </Button>
          <Toaster />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterToastNotification
          kind="info"
          title="New version available"
          subtitle="Reload the page to see the latest content."
        />
      </div>
    </div>
  ),
};

export const WithAction: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (no built-in action button on ToastNotification — use{' '}
          <code>ActionableNotification</code>)
        </div>
        <CarbonToastNotification
          kind="info"
          title="File uploaded"
          subtitle="report-2025-01.csv"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (<code>action</code> button on toast)
        </div>
        <div className="flex flex-col gap-3">
          <Button
            onClick={() =>
              toast('File uploaded', {
                description: 'report-2025-01.csv',
                action: {
                  label: 'Open',
                  onClick: () => console.log('Open clicked'),
                },
              })
            }
          >
            Toast with action
          </Button>
          <Toaster />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterToastNotification
          kind="info"
          title="File uploaded"
          subtitle="report-2025-01.csv"
        />
      </div>
    </div>
  ),
};

export const PromiseToast: Story = {
  name: 'Promise',
  render: () => {
    const trigger = () =>
      toast.promise(
        new globalThis.Promise<string>((resolve, reject) => {
          setTimeout(
            () => (Math.random() > 0.3 ? resolve('OK') : reject()),
            1500,
          );
        }),
        {
          loading: 'Saving…',
          success: 'Saved',
          error: 'Could not save',
        },
      );
    return (
      <div className="grid grid-cols-1 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn (toast.promise — Carbon has no equivalent)
          </div>
          <div className="flex flex-col gap-3">
            <Button onClick={trigger}>Run async (random success/error)</Button>
            <Toaster />
          </div>
        </div>
      </div>
    );
  },
};

export const Position: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (Toaster `position` prop — Carbon has no equivalent)
        </div>
        <div className="flex flex-col gap-3">
          <Button onClick={() => toast('Hello')}>Show toast</Button>
          <Toaster position="bottom-left" />
        </div>
      </div>
    </div>
  ),
};
