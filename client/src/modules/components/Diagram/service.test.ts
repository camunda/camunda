/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getPopoverPosition} from './service';
import {POPOVER_SIDE} from 'modules/constants';

function generateBoundary({top, left, height, width}: any) {
  return {
    top,
    left,
    height,
    width,
    bottom: top + height,
    right: left + width,
  };
}

describe('diagram service', () => {
  describe('getPopoverPosition', () => {
    it('should give BOTTOM position', () => {
      // given
      const diagramContainerBoundary = generateBoundary({
        top: 20,
        left: 100,
        height: 400,
        width: 400,
      });

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary,
      };

      const flowNodeBoundary = generateBoundary({
        top: 40,
        left: 100,
        height: 50,
        width: 50,
      });

      const flowNodeBBox = {
        width: 50,
        height: 50,
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox,
      };

      // when
      const position = getPopoverPosition({
        diagramContainer,
        flowNode,
        minHeight: 100,
        minWidth: 190,
      });

      // then
      expect(position.bottom).toBe(-16);
      expect(position.left).toBe(25);
      expect(position.side).toBe(POPOVER_SIDE.BOTTOM);
    });

    it('should give LEFT position', () => {
      // given
      const diagramContainerBoundary = generateBoundary({
        top: 20,
        left: 20,
        height: 400,
        width: 400,
      });

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary,
      };

      const flowNodeBoundary = generateBoundary({
        top: 50,
        left: 500,
        height: 380,
        width: 50,
      });

      const flowNodeBBox = {
        width: 50,
        height: 380,
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox,
      };

      // when
      const position = getPopoverPosition({
        diagramContainer,
        flowNode,
        minHeight: 100,
        minWidth: 190,
      });

      // then
      expect(position.left).toBe(-16);
      expect(position.top).toBe(190);
      expect(position.side).toBe(POPOVER_SIDE.LEFT);
    });

    it('should give TOP position', () => {
      // given
      const diagramContainerBoundary = generateBoundary({
        top: 20,
        left: 20,
        height: 400,
        width: 400,
      });

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary,
      };

      const flowNodeBoundary = generateBoundary({
        top: 350,
        left: 100,
        height: 50,
        width: 50,
      });

      const flowNodeBBox = {
        width: 50,
        height: 50,
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox,
      };

      // when
      const position = getPopoverPosition({
        diagramContainer,
        flowNode,
        minHeight: 100,
        minWidth: 190,
      });

      // then
      expect(position.top).toBe(-16);
      expect(position.left).toBe(25);
      expect(position.side).toBe(POPOVER_SIDE.TOP);
    });

    it('should give RIGHT position', () => {
      // given
      const diagramContainerBoundary = generateBoundary({
        top: 20,
        left: 20,
        height: 400,
        width: 400,
      });

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary,
      };

      const flowNodeBoundary = generateBoundary({
        top: 10,
        left: 100,
        height: 380,
        width: 50,
      });

      const flowNodeBBox = {
        width: 50,
        height: 380,
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox,
      };

      // when
      const position = getPopoverPosition({
        diagramContainer,
        flowNode,
        minHeight: 100,
        minWidth: 190,
      });

      // then
      expect(position.right).toBe(-16);
      expect(position.top).toBe(190);
      expect(position.side).toBe(POPOVER_SIDE.RIGHT);
    });

    it('should give BOTTOM MIRROR position', () => {
      // given
      const diagramContainerBoundary = generateBoundary({
        top: 20,
        left: 20,
        height: 400,
        width: 400,
      });

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary,
      };

      const flowNodeBoundary = generateBoundary({
        top: 10,
        left: 10,
        height: 380,
        width: 380,
      });

      const flowNodeBBox = {
        width: 380,
        height: 380,
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox,
      };

      // when
      const position = getPopoverPosition({
        diagramContainer,
        flowNode,
        minHeight: 100,
        minWidth: 190,
      });

      // then
      expect(position.bottom).toBe(16);
      expect(position.left).toBe(190);
      expect(position.side).toBe(POPOVER_SIDE.BOTTOM_MIRROR);
    });
  });
});
