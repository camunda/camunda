import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessSelection, __set__, __ResetDependency__} from 'main/processSelection/ProcessSelection';
import {LOADED_STATE, INITIAL_STATE} from 'utils/loading';

describe('<ProcessSelection>', () => {
  let PreviewCard;
  let loadProcessDefinitions;
  let openDefinition;
  let setVersionForProcess;
  let node;
  let update;
  let state;

  beforeEach(() => {
    PreviewCard = createMockComponent('PreviewCard');
    __set__('PreviewCard', PreviewCard);

    loadProcessDefinitions = sinon.spy();
    __set__('loadProcessDefinitions', loadProcessDefinitions);

    openDefinition = sinon.spy();
    __set__('openDefinition', openDefinition);

    setVersionForProcess = sinon.spy();
    __set__('setVersionForProcess', setVersionForProcess);

    ({node, update} = mountTemplate(<ProcessSelection />));
  });

  afterEach(() => {
    __ResetDependency__('PreviewCard');
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

    it('should display a preview of the definition', () => {
      expect(node.textContent).to.include('PreviewCard');
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

    it('should only display a single previewCard', () => {
      expect(node.querySelector('.row').textContent).to.eql('PreviewCard');
    });
  });
});
