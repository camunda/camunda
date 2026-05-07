/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Stack as CarbonStack} from '@carbon/react';
import {Stack as AdapterStack} from './stack.adapter';
import {Stack} from './stack.shadcn';

const meta: Meta = {
  title: 'UI/Stack',
};
export default meta;

type Story = StoryObj;

const Box = ({children}: {children: React.ReactNode}) => (
  <div className="rounded-md border bg-muted px-3 py-2 text-sm">{children}</div>
);

export const Vertical: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (gap=4)</div>
        <CarbonStack gap={4}>
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </CarbonStack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (gap=4)</div>
        <Stack gap={4}>
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </Stack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, gap=4)
        </div>
        <AdapterStack gap={4}>
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </AdapterStack>
      </div>
    </div>
  ),
};

export const Horizontal: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (orientation=horizontal)</div>
        <CarbonStack orientation="horizontal" gap={4}>
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </CarbonStack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (orientation=horizontal)</div>
        <Stack orientation="horizontal" gap={4}>
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </Stack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, orientation=horizontal)
        </div>
        <AdapterStack orientation="horizontal" gap={4}>
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </AdapterStack>
      </div>
    </div>
  ),
};

export const CustomGap: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (gap=&quot;1.5rem&quot;)</div>
        <CarbonStack gap="1.5rem">
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </CarbonStack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (gap=&quot;1.5rem&quot;)</div>
        <Stack gap="1.5rem">
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </Stack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, gap=&quot;1.5rem&quot;)
        </div>
        <AdapterStack gap="1.5rem">
          <Box>One</Box>
          <Box>Two</Box>
          <Box>Three</Box>
        </AdapterStack>
      </div>
    </div>
  ),
};

export const PolymorphicAs: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (as=&quot;ul&quot;)</div>
        <CarbonStack as="ul" gap={3}>
          <li>
            <Box>One</Box>
          </li>
          <li>
            <Box>Two</Box>
          </li>
        </CarbonStack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (as=&quot;ul&quot;)</div>
        <Stack as="ul" gap={3}>
          <li>
            <Box>One</Box>
          </li>
          <li>
            <Box>Two</Box>
          </li>
        </Stack>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, as=&quot;ul&quot;)
        </div>
        <AdapterStack as="ul" gap={3}>
          <li>
            <Box>One</Box>
          </li>
          <li>
            <Box>Two</Box>
          </li>
        </AdapterStack>
      </div>
    </div>
  ),
};
