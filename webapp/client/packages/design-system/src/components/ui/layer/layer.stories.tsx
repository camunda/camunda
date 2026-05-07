/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Layer as CarbonLayer} from '@carbon/react';
import {Layer as AdapterLayer} from './layer.adapter';
import {Layer} from './layer.shadcn';

const meta: Meta = {
  title: 'UI/Layer',
};
export default meta;

type Story = StoryObj;

const Box = ({children}: {children: React.ReactNode}) => (
  <div className="rounded-md border p-4 text-sm">{children}</div>
);

export const Nested: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonLayer>
          <Box>
            Layer 1
            <CarbonLayer>
              <Box>
                Layer 2
                <CarbonLayer>
                  <Box>Layer 2 (clamped)</Box>
                </CarbonLayer>
              </Box>
            </CarbonLayer>
          </Box>
        </CarbonLayer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Layer>
          <Box>
            Layer 1
            <Layer>
              <Box>
                Layer 2
                <Layer>
                  <Box>Layer 2 (clamped)</Box>
                </Layer>
              </Box>
            </Layer>
          </Box>
        </Layer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterLayer>
          <Box>
            Layer 1
            <AdapterLayer>
              <Box>
                Layer 2
                <AdapterLayer>
                  <Box>Layer 2 (clamped)</Box>
                </AdapterLayer>
              </Box>
            </AdapterLayer>
          </Box>
        </AdapterLayer>
      </div>
    </div>
  ),
};

export const WithBackground: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (withBackground)</div>
        <CarbonLayer withBackground>
          <div className="p-4 text-sm">Layer 1 with background</div>
          <CarbonLayer withBackground>
            <div className="p-4 text-sm">Layer 2 with background</div>
          </CarbonLayer>
        </CarbonLayer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (withBackground)</div>
        <Layer withBackground>
          <div className="p-4 text-sm">Layer 1 with background</div>
          <Layer withBackground>
            <div className="p-4 text-sm">Layer 2 with background</div>
          </Layer>
        </Layer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, withBackground)
        </div>
        <AdapterLayer withBackground>
          <div className="p-4 text-sm">Layer 1 with background</div>
          <AdapterLayer withBackground>
            <div className="p-4 text-sm">Layer 2 with background</div>
          </AdapterLayer>
        </AdapterLayer>
      </div>
    </div>
  ),
};

export const ExplicitLevel: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (level=2)</div>
        <CarbonLayer level={2} withBackground>
          <div className="p-4 text-sm">Forced level 2</div>
        </CarbonLayer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (level=2)</div>
        <Layer level={2} withBackground>
          <div className="p-4 text-sm">Forced level 2</div>
        </Layer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, level=2)
        </div>
        <AdapterLayer level={2} withBackground>
          <div className="p-4 text-sm">Forced level 2</div>
        </AdapterLayer>
      </div>
    </div>
  ),
};
