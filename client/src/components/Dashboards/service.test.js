/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {snapInPosition, collidesWithReport, applyPlacement} from './service';

jest.mock('request', () => {
  return {
    get: jest.fn(),
    del: jest.fn(),
    put: jest.fn()
  };
});

describe('snapInPosition', () => {
  const tileDimensions = {
    columns: 5,
    outerWidth: 10,
    outerHeight: 10,
    innerWidth: 8
  };
  const report = {position: {x: 0, y: 0}, dimensions: {width: 2, height: 2}};

  it('should calculate the closest grid position', () => {
    const snapped = snapInPosition({
      tileDimensions,
      report,
      changes: {x: 33, y: 8}
    });

    expect(snapped.position.x).toBe(3);
    expect(snapped.position.y).toBe(1);
  });

  it('should not allow a negative position', () => {
    const snapped = snapInPosition({
      tileDimensions,
      report,
      changes: {x: -37, y: 12}
    });

    expect(snapped.position.x).toBe(0);
    expect(snapped.position.y).toBe(1);
  });

  it('should not allow non-positive dimensions', () => {
    const snapped = snapInPosition({
      tileDimensions,
      report,
      changes: {width: -37, height: -12}
    });

    expect(snapped.dimensions.width).toBe(1);
    expect(snapped.dimensions.height).toBe(1);
  });

  it('should not crash when not provided any changes', () => {
    const snapped = snapInPosition({
      tileDimensions,
      report,
      changes: {}
    });
  });
});

describe('collidesWithReport', () => {
  it('shoud return true when a report is partially overlapping the specified placement', () => {
    const reports = [{position: {x: 0, y: 0}, dimensions: {width: 2, height: 1}}];
    const placement = {position: {x: 1, y: 0}, dimensions: {width: 2, height: 2}};

    expect(collidesWithReport({reports, placement})).toBe(true);
  });

  it('should return false when space is available', () => {
    const reports = [{position: {x: 0, y: 0}, dimensions: {width: 2, height: 1}}];
    const placement = {position: {x: 1, y: 1}, dimensions: {width: 2, height: 2}};

    expect(collidesWithReport({reports, placement})).toBe(false);
  });
});

describe('applyPlacement', () => {
  it('should style a node according to placement and tileDimensions', () => {
    const node = document.createElement('div');

    applyPlacement({
      placement: {position: {x: 3, y: 1}, dimensions: {width: 1, height: 1}},
      tileDimensions: {
        columns: 5,
        outerWidth: 10,
        outerHeight: 10,
        innerWidth: 8
      },
      node
    });

    expect(node.style.top).toBe('10px');
    expect(node.style.left).toBe('30px');
    expect(node.style.width).toBe('9px');
    expect(node.style.height).toBe('9px');
  });
});
