import React from 'react';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import ThemedDiagram from './Diagram';
import * as api from './api';

const {WrappedComponent: Diagram} = ThemedDiagram;

// mocking
jest.mock('bpmn-js/lib/NavigatedViewer');
const xmlMock = '<foo />';
api.getWorkflowXML = mockResolvedAsyncFn(xmlMock);

describe('Diagram', () => {
  const workflowDefinitionId = 'some-id';
  let node;

  beforeEach(() => {
    node = shallow(
      <Diagram workflowDefinitionId={workflowDefinitionId} theme={'dark'} />
    );
  });

  it('should set initial containerNode and Viewer to null', () => {
    // given
    const diagramInstance = new Diagram();

    // then
    expect(diagramInstance.Viewer).toBe(null);
    expect(diagramInstance.containerNode).toBe(null);
  });

  // TODO:
  // 2- should call api.getWorkflowXML with workflowDefinitionId
  // 3- should initiate BPMNViewer with correct config
  // 4- should call Viewer.importXML and handle error correctly
  // and call handleZoom
  // 8- render
  describe('componentDidMount', async () => {
    it('should get xml from api and import it in Viewer', async () => {
      // when
      await node.instance().componentDidMount();

      // then
      expect(api.getWorkflowXML).toBeCalledWith(workflowDefinitionId);
    });
  });

  describe('containerRef', () => {
    it('should set containerNode to provided node', () => {
      // given
      const diagramInstance = new Diagram();
      const someNode = 'some/node';

      // when
      diagramInstance.containerRef(someNode);

      // then
      expect(diagramInstance.containerNode).toBe(someNode);
    });
  });

  describe('handleZoom', () => {
    it('should call stepZoom with provided step', async () => {
      // given
      await node.instance().componentDidMount();
      const step = 1;
      const stepZoomMock = node.instance().Viewer.zoomScroll.stepZoom;

      // when
      node.instance().handleZoom(step);

      // then
      expect(stepZoomMock).toBeCalledWith(step);
    });
  });

  describe('handleZoomIn', () => {
    it('should call handleZoom with 0.1 step', () => {
      // given
      const handleZoomSpy = jest.spyOn(node.instance(), 'handleZoom');

      // when
      node.instance().handleZoomIn();

      // then
      expect(handleZoomSpy).toBeCalledWith(0.1);
    });
  });

  describe('handleZoomOut', () => {
    it('should call handleZoom with -0.1 step', () => {
      // given
      const handleZoomSpy = jest.spyOn(node.instance(), 'handleZoom');

      // when
      node.instance().handleZoomOut();

      // then
      expect(handleZoomSpy).toBeCalledWith(-0.1);
    });
  });

  describe('handleZoomReset', () => {
    it('should reset zoom in canvas', async () => {
      // given
      await node.instance().componentDidMount();
      const zoom = node.instance().Viewer.canvas.zoom;

      // when
      node.instance().handleZoomReset();

      // then
      expect(zoom).toBeCalledWith('fit-viewport', 'auto');
    });
  });
});
