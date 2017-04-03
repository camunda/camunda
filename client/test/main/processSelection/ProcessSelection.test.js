import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessSelection, __set__, __ResetDependency__} from 'main/processSelection/ProcessSelection';
import {LOADED_STATE, INITIAL_STATE} from 'utils/loading';

describe('<ProcessSelection>', () => {
  let DiagramPreview;
  let loadProcessDefinitions;
  let openDefinition;
  let setVersionForProcess;
  let node;
  let update;
  let state;

  beforeEach(() => {
    DiagramPreview = createMockComponent('DiagramPreview');
    __set__('DiagramPreview', DiagramPreview);

    loadProcessDefinitions = sinon.spy();
    __set__('loadProcessDefinitions', loadProcessDefinitions);

    openDefinition = sinon.spy();
    __set__('openDefinition', openDefinition);

    setVersionForProcess = sinon.spy();
    __set__('setVersionForProcess', setVersionForProcess);

    ({node, update} = mountTemplate(<ProcessSelection />));
  });

  afterEach(() => {
    __ResetDependency__('DiagramPreview');
    __ResetDependency__('loadProcessDefinitions');
    __ResetDependency__('openDefinition');
    __ResetDependency__('setVersionForProcess');
  });

  it('should display a hint when no process Definitions are present', () => {
    update({processDefinitions:{
      state: LOADED_STATE,
      data: []
    }});

    expect(node.querySelector('.no-definitions')).to.exist;
  });

  it('should load the list of available definitions', () => {
    update({processDefinitions: {
      state: INITIAL_STATE
    }});

    expect(loadProcessDefinitions.calledOnce).to.eql(true);
  });

  describe('single version', () => {
    beforeEach(() => {
      state = {
        processDefinitions: {
          state: LOADED_STATE,
          data: [{
            id: 'processId',
            key: 'processKey',
            name: 'processName',
            version: 4,
            bpmn20Xml: 'some xml'
          }]
        },
        versions: {}
      };

      update(state);
    });

    it('should display a diagram preview of the definition', () => {
      expect(node.textContent).to.include('DiagramPreview');
    });

    it('should display the name of the process definition', () => {
      expect(node.textContent).to.include('processName');
    });

    it('should display the version of the process definition', () => {
      expect(node.textContent).to.include('4');
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
        processDefinitions: {
          state: LOADED_STATE,
          data: [{
            id: 'processId1',
            key: 'processKey',
            name: 'processName 1',
            version: 1,
            bpmn20Xml: 'some xml 1'
          }, {
            id: 'processId2',
            key: 'processKey',
            name: 'processName 2',
            version: 2,
            bpmn20Xml: 'some xml 2'
          }]
        },
        versions: {}
      };

      update(state);
    });

    it('should display a select to choose a version', () => {
      expect(node.querySelector('select')).to.exist;
    });

    it('should only display a single diagram', () => {
      expect(node.querySelectorAll('.diagram').length).to.eql(1);
    });

    it('should display the latest version by default', () => {
      expect(node.querySelector('select').value).to.eql('v2');
      expect(node.querySelector('.name').textContent).to.eql('processName 2');
    });

    it('should set the version when switching the version', () => {
      node.querySelector('select').selectedIndex = 1;
      triggerEvent({
        node: node,
        selector: 'select',
        eventName: 'change'
      });

      expect(setVersionForProcess.calledOnce).to.eql(true);
      expect(setVersionForProcess.calledWith('processKey', 1)).to.eql(true);
    });

    it('should open the selected version', () => {
      state.versions.processKey = 1;
      update(state);

      triggerEvent({
        node: node,
        selector: '.diagram',
        eventName: 'click'
      });

      expect(openDefinition.calledOnce).to.eql(true);
      expect(openDefinition.calledWith('processId1')).to.eql(true);
    });
  });
});
