import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessSelection, __set__, __ResetDependency__} from 'main/processSelection/ProcessSelection';
import {LOADED_STATE, INITIAL_STATE} from 'utils/loading';

describe('<ProcessSelection>', () => {
  let DiagramPreview;
  let loadProcessDefinitions;
  let openDefinition;
  let node;
  let update;
  let state;

  beforeEach(() => {
    state = {
      processDefinitions: {
        state: LOADED_STATE,
        data: [{
          id: 'processId',
          key: 'processKey',
          name: 'processName',
          bpmn20Xml: 'some xml'
        }]
      }
    };

    DiagramPreview = createMockComponent('DiagramPreview');
    __set__('DiagramPreview', DiagramPreview);

    loadProcessDefinitions = sinon.spy();
    __set__('loadProcessDefinitions', loadProcessDefinitions);

    openDefinition = sinon.spy();
    __set__('openDefinition', openDefinition);

    ({node, update} = mountTemplate(<ProcessSelection />));
  });

  afterEach(() => {
    __ResetDependency__('DiagramPreview');
    __ResetDependency__('loadProcessDefinitions');
    __ResetDependency__('openDefinition');
  });

  it('should display a hint when no process Definitions are present', () => {
    state.processDefinitions.data = [];
    update(state);

    expect(node.querySelector('.no-definitions')).to.exist;
  });

  it('should load the list of available definitions', () => {
    state.processDefinitions.data = undefined;
    state.processDefinitions.state = INITIAL_STATE;
    update(state);

    expect(loadProcessDefinitions.calledOnce).to.eql(true);
  });

  it('should display a diagram preview of the definition', () => {
    update(state);

    expect(node.textContent).to.include('DiagramPreview');
  });

  it('should display the name of the process definition', () => {
    update(state);

    expect(node.textContent).to.include('processName');
  });
});
