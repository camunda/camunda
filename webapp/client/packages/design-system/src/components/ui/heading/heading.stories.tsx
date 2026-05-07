/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Section as CarbonSection, Heading as CarbonHeading} from '@carbon/react';
import {Heading} from './heading.shadcn';
import {Section} from '../section/section.shadcn';

const meta: Meta = {
  title: 'UI/Heading',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (no Section, defaults to h1)</div>
        <CarbonHeading>Standalone heading</CarbonHeading>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (no Section, defaults to h1)</div>
        <Heading>Standalone heading</Heading>
      </div>
    </div>
  ),
};

export const NestedHierarchy: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonSection>
          <CarbonHeading>h1</CarbonHeading>
          <CarbonSection>
            <CarbonHeading>h2</CarbonHeading>
            <CarbonSection>
              <CarbonHeading>h3</CarbonHeading>
              <CarbonSection>
                <CarbonHeading>h4</CarbonHeading>
              </CarbonSection>
            </CarbonSection>
          </CarbonSection>
        </CarbonSection>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Section>
          <Heading>h1</Heading>
          <Section>
            <Heading>h2</Heading>
            <Section>
              <Heading>h3</Heading>
              <Section>
                <Heading>h4</Heading>
              </Section>
            </Section>
          </Section>
        </Section>
      </div>
    </div>
  ),
};

export const CustomClassName: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonHeading className="!text-primary">Tinted heading</CarbonHeading>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Heading className="text-primary">Tinted heading</Heading>
      </div>
    </div>
  ),
};
