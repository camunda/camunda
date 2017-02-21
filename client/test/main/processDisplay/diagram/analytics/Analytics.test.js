import {expect} from 'chai';
import sinon from 'sinon';
import {createMockComponent, mountTemplate} from 'testHelpers';
import {createAnalyticsRenderer,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/Analytics';

describe('<Analytics>', () => {
  let createModal;
  let viewer;
  let Modal;
  let update;

  let diagramElement;
  let state;

  beforeEach(() => {
    diagramElement = {
      type: 'bpmn:Task',
      name: 'Some Task',
      id: 'act2'
    };

    state = {heatmap: {
      data: {
        act1: 1,
        act2: 2
      }
    }};

    Modal = createMockComponent('Modal');
    Modal.open = sinon.spy();
    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    viewer = {
      get: sinon.stub().returnsThis(),
      on: sinon.spy()
    };

    ({update} = mountTemplate((node, eventsBus) => createAnalyticsRenderer({viewer, node, eventsBus})));
    update(state);
  });

  afterEach(() => {
    __ResetDependency__('createModal');
  });

  it('should do nothing when a non end event is clicked', () => {
    viewer.on.lastCall.args[1]({element: diagramElement});

    expect(Modal.open.called).to.eql(false);
  });
});
