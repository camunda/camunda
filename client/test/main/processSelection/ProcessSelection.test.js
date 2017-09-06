import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {ProcessSelectionReact, __set__, __ResetDependency__} from 'main/processSelection/ProcessSelection';
import {LOADED_STATE, INITIAL_STATE} from 'utils/loading';
import React from 'react';
import {mount} from 'enzyme';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<ProcessSelection>', () => {
  let loadProcessDefinitions;
  let PreviewCard;
  let wrapper;

  beforeEach(() => {
    PreviewCard = createReactMock('PreviewCard');
    __set__('PreviewCard', PreviewCard);

    loadProcessDefinitions = sinon.spy();
    __set__('loadProcessDefinitions', loadProcessDefinitions);
  });

  afterEach(() => {
    __ResetDependency__('loadProcessDefinitions');
    __ResetDependency__('PreviewCard');
  });

  it('should display a hint when no process Definitions are present', () => {
    const processDefinitions = {
      state: LOADED_STATE,
      data: {
        list: []
      }
    };

    wrapper = mount(<ProcessSelectionReact processDefinitions={processDefinitions} />);

    expect(wrapper.find('.no-definitions')).to.be.present();
  });

  it('should load the list of available definitions', () => {
    wrapper = mount(<ProcessSelectionReact />);

    expect(loadProcessDefinitions.calledOnce).to.eql(true);
  });

  it('should pass loading true to LoadingIndicator while processDefinitions are not loaded', () => {
    const processDefinitions = {
      state: INITIAL_STATE
    };

    wrapper = mount(<ProcessSelectionReact processDefinitions={processDefinitions} />);
  });

  it('should display a preview of the definition', () => {
    const current = {
      id: 'processId',
      key: 'processKey',
      name: 'processName',
      version: 4,
      bpmn20Xml: 'some xml'
    };
    const versions = [];
    const engineCount = 34;

    const processDefinitions = {
      state: LOADED_STATE,
      data: {
        engineCount,
        list: [{
          current,
          versions
        }]
      }
    };

    wrapper = mount(<ProcessSelectionReact processDefinitions={processDefinitions} />);

    expect(wrapper).to.contain.text('PreviewCard');
    expect(
      PreviewCard.calledWith({
        current,
        versions,
        engineCount
      })
    ).to.eql(true);
  });
});
