import {getPopoverPostion} from './service';
import {POPOVER_SIDE} from 'modules/constants';

describe('diagram service', () => {
  describe('getPopoverPostion', () => {
    it('should give BOTTOM position', () => {
      // given
      const diagramContainerBoundary = {
        top: 20,
        left: 20,
        right: 20,
        bottom: 20,
        height: 400,
        width: 400
      };

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary
      };

      const flowNodeBoundary = {
        top: 40,
        left: 100,
        right: 300,
        bottom: 360,
        height: 50,
        width: 50
      };

      const flowNodeBBox = {
        width: 50,
        height: 50
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox
      };

      // when
      const position = getPopoverPostion({diagramContainer, flowNode});

      // then
      expect(position.bottom).toBe(-16);
      expect(position.left).toBe(25);
      expect(position.side).toBe(POPOVER_SIDE.BOTTOM);
    });

    it('should give LEFT position', () => {
      // given
      const diagramContainerBoundary = {
        top: 20,
        left: 20,
        right: 20,
        bottom: 20,
        height: 400,
        width: 400
      };

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary
      };

      const flowNodeBoundary = {
        top: 10,
        left: 300,
        right: 100,
        bottom: 10,
        height: 380,
        width: 50
      };

      const flowNodeBBox = {
        width: 50,
        height: 380
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox
      };

      // when
      const position = getPopoverPostion({diagramContainer, flowNode});

      // then
      expect(position.left).toBe(-16);
      expect(position.top).toBe(190);
      expect(position.side).toBe(POPOVER_SIDE.LEFT);
    });

    it('should give TOP position', () => {
      // given
      const diagramContainerBoundary = {
        top: 20,
        left: 20,
        right: 20,
        bottom: 20,
        height: 400,
        width: 400
      };

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary
      };

      const flowNodeBoundary = {
        top: 350,
        left: 100,
        right: 300,
        bottom: 50,
        height: 50,
        width: 50
      };

      const flowNodeBBox = {
        width: 50,
        height: 50
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox
      };

      // when
      const position = getPopoverPostion({diagramContainer, flowNode});

      // then
      expect(position.top).toBe(-16);
      expect(position.left).toBe(25);
      expect(position.side).toBe(POPOVER_SIDE.TOP);
    });

    it('should give RIGHT position', () => {
      // given
      const diagramContainerBoundary = {
        top: 20,
        left: 20,
        right: 20,
        bottom: 20,
        height: 400,
        width: 400
      };

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary
      };

      const flowNodeBoundary = {
        top: 10,
        left: 100,
        right: 300,
        bottom: 10,
        height: 380,
        width: 50
      };

      const flowNodeBBox = {
        width: 50,
        height: 380
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox
      };

      // when
      const position = getPopoverPostion({diagramContainer, flowNode});

      // then
      expect(position.right).toBe(-16);
      expect(position.top).toBe(190);
      expect(position.side).toBe(POPOVER_SIDE.RIGHT);
    });

    it('should give BOTTOM MIRROR position', () => {
      // given
      const diagramContainerBoundary = {
        top: 20,
        left: 20,
        right: 20,
        bottom: 20,
        height: 400,
        width: 400
      };

      const diagramContainer = {
        getBoundingClientRect: () => diagramContainerBoundary
      };

      const flowNodeBoundary = {
        top: 10,
        left: 10,
        right: 10,
        bottom: 10,
        height: 380,
        width: 380
      };

      const flowNodeBBox = {
        width: 380,
        height: 380
      };

      const flowNode = {
        getBoundingClientRect: () => flowNodeBoundary,
        getBBox: () => flowNodeBBox
      };

      // when
      const position = getPopoverPostion({diagramContainer, flowNode});

      // then
      expect(position.bottom).toBe(16);
      expect(position.left).toBe(190);
      expect(position.side).toBe(POPOVER_SIDE.BOTTOM_MIRROR);
    });
  });
});
