/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Grid as CarbonGrid, Column as CarbonColumn} from '@carbon/react';
import {Column} from './column.shadcn';
import {Grid} from '../grid/grid.shadcn';

const meta: Meta = {
  title: 'UI/Column',
};
export default meta;

type Story = StoryObj;

const Box = ({children}: {children: React.ReactNode}) => (
  <div className="rounded-md border bg-muted px-3 py-2 text-sm">{children}</div>
);

export const ConstantSpan: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonGrid>
          <CarbonColumn span={4}>
            <Box>4</Box>
          </CarbonColumn>
          <CarbonColumn span={4}>
            <Box>4</Box>
          </CarbonColumn>
          <CarbonColumn span={4}>
            <Box>4</Box>
          </CarbonColumn>
          <CarbonColumn span={4}>
            <Box>4</Box>
          </CarbonColumn>
        </CarbonGrid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Grid>
          <Column span={4}>
            <Box>4</Box>
          </Column>
          <Column span={4}>
            <Box>4</Box>
          </Column>
          <Column span={4}>
            <Box>4</Box>
          </Column>
          <Column span={4}>
            <Box>4</Box>
          </Column>
        </Grid>
      </div>
    </div>
  ),
};

export const StartEnd: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (start=5, end=13)</div>
        <CarbonGrid>
          <CarbonColumn span={{start: 5, end: 13}}>
            <Box>start=5 end=13</Box>
          </CarbonColumn>
        </CarbonGrid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (start=5, end=13)</div>
        <Grid>
          <Column span={{start: 5, end: 13}}>
            <Box>start=5 end=13</Box>
          </Column>
        </Grid>
      </div>
    </div>
  ),
};

export const Offset: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (offset=4, span=8)</div>
        <CarbonGrid>
          <CarbonColumn span={{offset: 4, span: 8}}>
            <Box>offset=4 span=8</Box>
          </CarbonColumn>
        </CarbonGrid>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (offset=4, span=8)</div>
        <Grid>
          <Column span={{offset: 4, span: 8}}>
            <Box>offset=4 span=8</Box>
          </Column>
        </Grid>
      </div>
    </div>
  ),
};
