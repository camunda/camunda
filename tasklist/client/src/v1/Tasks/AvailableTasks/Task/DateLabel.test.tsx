/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {formatDate} from 'common/dates/formatDateRelative';
import {DateLabel} from './DateLabel';

describe('DateLabel', () => {
  it('uses the absolute title for far dates when the difference is less than a week', () => {
    const date = formatDate(
      new Date(Date.parse('2024-01-04T00:00:00Z')),
      new Date(Date.parse('2024-01-01T00:00:00Z')),
    );

    render(
      <DateLabel
        date={date}
        relativeLabel="Relative"
        absoluteLabel="Absolute"
      />,
    );

    expect(screen.getByTitle('Absolute Thursday')).toBeInTheDocument();
    expect(screen.getByText('Thursday')).toBeInTheDocument();
  });

  it('uses the absolute title for far dates when the difference is a month', () => {
    const date = formatDate(
      new Date(Date.parse('2024-02-01T00:00:00Z')),
      new Date(Date.parse('2024-01-01T00:00:00Z')),
    );

    render(
      <DateLabel
        date={date}
        relativeLabel="Relative"
        absoluteLabel="Absolute"
      />,
    );

    expect(screen.getByTitle('Absolute 1st of February')).toBeInTheDocument();
    expect(screen.getByText('1 Feb')).toBeInTheDocument();
  });

  it('uses the absolute title for far dates when the difference is a year', () => {
    const date = formatDate(
      new Date(Date.parse('2025-01-01T00:00:00Z')),
      new Date(Date.parse('2024-01-01T00:00:00Z')),
    );

    render(
      <DateLabel
        date={date}
        relativeLabel="Relative"
        absoluteLabel="Absolute"
      />,
    );

    expect(
      screen.getByTitle('Absolute 1st of January, 2025'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('1 Jan 2025')).toHaveLength(2);
  });

  it('uses the relative title for far dates is the difference is tomorrow', () => {
    const date = formatDate(
      new Date(Date.parse('2024-01-02T00:00:00Z')),
      new Date(Date.parse('2024-01-01T00:00:00Z')),
    );

    render(
      <DateLabel
        date={date}
        relativeLabel="Relative"
        absoluteLabel="Absolute"
      />,
    );

    expect(screen.getByTitle('Relative Tomorrow')).toBeInTheDocument();
    expect(screen.getByText('Tomorrow')).toBeInTheDocument();
  });

  it('uses the relative title for far dates is the difference is an hour', () => {
    const date = formatDate(
      new Date(Date.parse('2024-01-01T00:00:00Z')),
      new Date(Date.parse('2024-01-01T01:00:00Z')),
    );

    render(
      <DateLabel
        date={date}
        relativeLabel="Relative"
        absoluteLabel="Absolute"
      />,
    );

    expect(screen.getByTitle('Relative 60 minutes ago')).toBeInTheDocument();
    expect(screen.getByText('60 minutes ago')).toBeInTheDocument();
  });

  it('uses the relative title for far dates is the difference is nothing', () => {
    const date = formatDate(
      new Date(Date.parse('2024-01-01T00:00:00Z')),
      new Date(Date.parse('2024-01-01T00:00:00Z')),
    );

    render(
      <DateLabel
        date={date}
        relativeLabel="Relative"
        absoluteLabel="Absolute"
      />,
    );

    expect(screen.getByTitle('Relative Now')).toBeInTheDocument();
    expect(screen.getByText('Now')).toBeInTheDocument();
  });
});
