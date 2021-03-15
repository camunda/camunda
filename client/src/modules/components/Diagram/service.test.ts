/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getPopoverPosition} from './service';

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
      const position = getPopoverPosition(
        {
          diagramContainer,
          flowNode,
          minHeight: 100,
          minWidth: 190,
        },
        false
      );

      // then
      expect(position.overlay.bottom).toBe(-16);
      expect(position.overlay.left).toBe(25);
      expect(position.side).toBe('BOTTOM');
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
      const position = getPopoverPosition(
        {
          diagramContainer,
          flowNode,
          minHeight: 100,
          minWidth: 190,
        },
        false
      );

      // then
      expect(position.overlay.left).toBe(-16);
      expect(position.overlay.top).toBe(190);
      expect(position.side).toBe('LEFT');
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
      const position = getPopoverPosition(
        {
          diagramContainer,
          flowNode,
          minHeight: 100,
          minWidth: 190,
        },
        false
      );

      // then
      expect(position.overlay.top).toBe(-16);
      expect(position.overlay.left).toBe(25);
      expect(position.side).toBe('TOP');
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
      const position = getPopoverPosition(
        {
          diagramContainer,
          flowNode,
          minHeight: 100,
          minWidth: 190,
        },
        false
      );

      // then
      expect(position.overlay.right).toBe(-16);
      expect(position.overlay.top).toBe(190);
      expect(position.side).toBe('RIGHT');
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
      const position = getPopoverPosition(
        {
          diagramContainer,
          flowNode,
          minHeight: 100,
          minWidth: 190,
        },
        false
      );

      // then
      expect(position.overlay.bottom).toBe(16);
      expect(position.overlay.left).toBe(190);
      expect(position.side).toBe('BOTTOM_MIRROR');
    });
  });
});
