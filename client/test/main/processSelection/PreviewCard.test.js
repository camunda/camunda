import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {PreviewCard, __set__, __ResetDependency__} from 'main/processSelection/PreviewCard';

describe('<PreviewCard>', () => {
  let DiagramPreview;
  let createDiagramPreview;
  let openDefinition;
  let setVersionForProcess;
  let node;
  let update;
  let state;

  beforeEach(() => {
    DiagramPreview = createMockComponent('DiagramPreview');
    DiagramPreview.setLoading = sinon.spy();

    createDiagramPreview = sinon.stub().returns(DiagramPreview);
    __set__('createDiagramPreview', createDiagramPreview);

    openDefinition = sinon.spy();
    __set__('openDefinition', openDefinition);

    setVersionForProcess = sinon.spy();
    __set__('setVersionForProcess', setVersionForProcess);

    ({node, update} = mountTemplate(<PreviewCard />));
  });

  afterEach(() => {
    __ResetDependency__('createDiagramPreview');
    __ResetDependency__('openDefinition');
    __ResetDependency__('setVersionForProcess');
  });

  describe('single version', () => {
    beforeEach(() => {
      state = {
        current: {
          id: 'processId',
          key: 'processKey',
          name: 'processName',
          version: 1,
          bpmn20Xml: 'some xml'
        },
        versions: [{
          id: 'processId',
          key: 'processKey',
          version: 1
        }]
      };

      update(state);
    });

    it('should display a preview diagram of the definition', () => {
      expect(node.textContent).to.include('DiagramPreview');
    });

    it('should display the name of the process definition', () => {
      expect(node.textContent).to.include('processName');
    });

    it('should display the version of the process definition', () => {
      expect(node.textContent).to.include('1');
    });

    it('should not display a select element to choose a version', () => {
      expect(node.querySelector('select')).to.not.exist;
    });

    it('should open the definition when clicking on the diagram', () => {
      triggerEvent({
        node: node,
        selector: '.diagram',
        eventName: 'click'
      });

      expect(openDefinition.calledOnce).to.eql(true);
      expect(openDefinition.calledWith('processId')).to.eql(true);
    });
  });

  describe('multiple versions', () => {
    beforeEach(() => {
      state = {
        current: {
          id: 'processId',
          key: 'processKey',
          name: 'processName',
          version: 2,
          bpmn20Xml: 'some xml',
        },
        versions: [{
          id: 'processId',
          key: 'processKey',
          version: 2
        }, {
          id: 'processId1',
          version: 1,
          key: 'processKey'
        }]
      };

      update(state);
    });

    it('should display a select to choose a version', () => {
      expect(node.querySelector('select')).to.exist;
    });

    it('should only display a single diagram', () => {
      expect(node.querySelectorAll('.diagram').length).to.eql(1);
    });

    it('should set the version when switching the version', () => {
      node.querySelector('select').selectedIndex = 1;
      triggerEvent({
        node: node,
        selector: 'select',
        eventName: 'change'
      });

      expect(setVersionForProcess.calledOnce).to.eql(true);
      expect(setVersionForProcess.calledWith(state.versions[1])).to.eql(true);
    });

    it('should trigger loading of diagram preview when switching the version', () => {
      node.querySelector('select').selectedIndex = 1;
      triggerEvent({
        node: node,
        selector: 'select',
        eventName: 'change'
      });

      expect(DiagramPreview.setLoading.calledWith(true)).to.eql(true);
    });

    it('should open the selected version', () => {
      state.versions.version = 1;
      update(state);

      triggerEvent({
        node: node,
        selector: '.diagram',
        eventName: 'click'
      });

      expect(openDefinition.calledOnce).to.eql(true);
      expect(openDefinition.calledWith('processId')).to.eql(true);
    });
  });
});
