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
import {
  ComposedModal as AdapterComposedModal,
  Modal as AdapterModal,
  ModalBody as AdapterModalBody,
  ModalFooter as AdapterModalFooter,
  ModalHeader as AdapterModalHeader,
} from './modal.adapter';
import {
  ComposedModal as CarbonComposedModal,
  Modal as CarbonModal,
  ModalBody as CarbonModalBody,
  ModalFooter as CarbonModalFooter,
  ModalHeader as CarbonModalHeader,
} from './modal.carbon';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './modal.shadcn';

const meta: Meta = {
  title: 'UI/Modal',
};
export default meta;

type Story = StoryObj;

const CarbonControlled = ({
  triggerLabel,
  ...modalProps
}: {
  triggerLabel: string;
} & React.ComponentProps<typeof CarbonModal>) => {
  const [open, setOpen] = React.useState(false);
  return (
    <>
      <button
        type="button"
        className="underline"
        onClick={() => setOpen(true)}
      >
        {triggerLabel}
      </button>
      <CarbonModal
        {...modalProps}
        open={open}
        onRequestClose={() => setOpen(false)}
      />
    </>
  );
};

const AdapterControlled = ({
  triggerLabel,
  ...modalProps
}: {
  triggerLabel: string;
} & React.ComponentProps<typeof AdapterModal>) => {
  const [open, setOpen] = React.useState(false);
  return (
    <>
      <button
        type="button"
        className="underline"
        onClick={() => setOpen(true)}
      >
        {triggerLabel}
      </button>
      <AdapterModal
        {...modalProps}
        open={open}
        onRequestClose={() => setOpen(false)}
      />
    </>
  );
};

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonControlled
          triggerLabel="Open Carbon modal"
          modalHeading="Save changes?"
          modalLabel="Profile"
          primaryButtonText="Save"
          secondaryButtonText="Cancel"
        >
          <p className="text-sm">
            Your changes will be applied to your profile.
          </p>
        </CarbonControlled>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Dialog>
          <DialogTrigger asChild>
            <button type="button" className="underline">
              Open shadcn dialog
            </button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Save changes?</DialogTitle>
              <DialogDescription>
                Your changes will be applied to your profile.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose asChild>
                <Button variant="outline">Cancel</Button>
              </DialogClose>
              <Button>Save</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterControlled
          triggerLabel="Open Adapter modal"
          modalHeading="Save changes?"
          modalLabel="Profile"
          primaryButtonText="Save"
          secondaryButtonText="Cancel"
        >
          <p className="text-sm">
            Your changes will be applied to your profile.
          </p>
        </AdapterControlled>
      </div>
    </div>
  ),
};

export const ComposedAPI: Story = {
  render: () => {
    const Carbon = () => {
      const [open, setOpen] = React.useState(false);
      return (
        <>
          <button
            type="button"
            className="underline"
            onClick={() => setOpen(true)}
          >
            Open Carbon ComposedModal
          </button>
          <CarbonComposedModal
            open={open}
            onClose={() => setOpen(false)}
            preventCloseOnClickOutside
          >
            <CarbonModalHeader title="Edit profile" label="Account settings" />
            <CarbonModalBody>
              <p className="text-sm">Make changes and save when done.</p>
            </CarbonModalBody>
            <CarbonModalFooter
              primaryButtonText="Save"
              secondaryButtonText="Cancel"
              onRequestClose={() => setOpen(false)}
            >
              {null}
            </CarbonModalFooter>
          </CarbonComposedModal>
        </>
      );
    };
    const Adapter = () => {
      const [open, setOpen] = React.useState(false);
      return (
        <>
          <button
            type="button"
            className="underline"
            onClick={() => setOpen(true)}
          >
            Open Adapter ComposedModal
          </button>
          <AdapterComposedModal
            open={open}
            onClose={() => setOpen(false)}
            preventCloseOnClickOutside
          >
            <AdapterModalHeader title="Edit profile" label="Account settings" />
            <AdapterModalBody>
              <p className="text-sm">Make changes and save when done.</p>
            </AdapterModalBody>
            <AdapterModalFooter
              primaryButtonText="Save"
              secondaryButtonText="Cancel"
              onRequestClose={() => setOpen(false)}
            >
              {null}
            </AdapterModalFooter>
          </AdapterComposedModal>
        </>
      );
    };
    return (
      <div className="grid grid-cols-3 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            Carbon (<code>ComposedModal</code> family)
          </div>
          <Carbon />
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn (Dialog is always composed)
          </div>
          <Dialog>
            <DialogTrigger asChild>
              <button type="button" className="underline">
                Open shadcn dialog
              </button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Edit profile</DialogTitle>
                <DialogDescription>
                  Make changes and save when done.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose asChild>
                  <Button variant="outline">Cancel</Button>
                </DialogClose>
                <Button>Save</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
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

export const Destructive: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>danger</code> kind)
        </div>
        <CarbonControlled
          triggerLabel="Delete record"
          danger
          modalHeading="Delete record"
          modalLabel="Permanent action"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
        >
          <p className="text-sm">
            This cannot be undone. The record will be removed permanently.
          </p>
        </CarbonControlled>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (use <code>AlertDialog</code> for destructive confirmations)
        </div>
        <Dialog>
          <DialogTrigger asChild>
            <button type="button" className="underline">
              Delete record
            </button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Delete record</DialogTitle>
              <DialogDescription>
                This cannot be undone. The record will be removed permanently.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose asChild>
                <Button variant="outline">Cancel</Button>
              </DialogClose>
              <Button variant="destructive">Delete</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterControlled
          triggerLabel="Delete record"
          danger
          modalHeading="Delete record"
          modalLabel="Permanent action"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
        >
          <p className="text-sm">
            This cannot be undone. The record will be removed permanently.
          </p>
        </AdapterControlled>
      </div>
    </div>
  ),
};
