/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DashboardTile} from 'types';

import {getTileTopNLimit} from './tilePagination';

const tileWithTopN = (topN?: unknown): DashboardTile =>
  ({configuration: {topN}}) as unknown as DashboardTile;

it('should return the parsed limit for a positive integer string', () => {
  expect(getTileTopNLimit(tileWithTopN('5'))).toBe(5);
});

it('should return undefined when the tile is missing', () => {
  expect(getTileTopNLimit(undefined)).toBeUndefined();
});

it('should return undefined when topN is not configured', () => {
  expect(getTileTopNLimit(tileWithTopN(undefined))).toBeUndefined();
});

it('should return undefined for a non-numeric value', () => {
  expect(getTileTopNLimit(tileWithTopN('abc'))).toBeUndefined();
});

it('should return undefined for a non-integer value', () => {
  expect(getTileTopNLimit(tileWithTopN('5.5'))).toBeUndefined();
});

it('should return undefined for zero or negative values', () => {
  expect(getTileTopNLimit(tileWithTopN('0'))).toBeUndefined();
  expect(getTileTopNLimit(tileWithTopN('-3'))).toBeUndefined();
});
