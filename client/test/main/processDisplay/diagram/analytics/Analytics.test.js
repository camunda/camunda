import {expect} from 'chai';
import sinon from 'sinon';
import {createEventsBus} from 'view-utils';
import {createMockComponent} from 'testHelpers';
import {createAnalyticsRenderer,
        __set__, __ResetDependency__} from 'main/processDisplay/diagram/analytics/Analytics';

describe('<Analytics>', () => {
  let clickElement;
  let createModal;
  let viewer;
  let node;
  let eventsBus;
  let clickFct;
  let Modal;

  beforeEach(() => {
    Modal = createMockComponent('Modal');
    Modal.open = sinon.spy();
    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    clickElement = sinon.stub().returns(true);
    __set__('clickElement', clickElement);

    node = document.getElementById('dom-testing-target');
    eventsBus = createEventsBus();

    viewer = {
      get: sinon.stub().returns({
        on: (type, cb) => clickFct = cb
      })
    };

    createAnalyticsRenderer({viewer, node, eventsBus});
  });

  afterEach(() => {
    __ResetDependency__('clickElement');
    __ResetDependency__('createModal');
  });

  it('should call the clickElement handler on element click', () => {
    clickFct({element: 'someElement'});

    expect(clickElement.called).to.eql(true);
  });

  it('should open the modal dialog when clickElement returns true', () => {
    clickFct({element: 'someElement'});

    expect(Modal.open.called).to.eql(true);
  });

  it('should not open the modal dialog when clickElement returns false', () => {
    clickElement.returns(false);

    clickFct({element: 'someElement'});

    expect(Modal.open.called).to.eql(false);
  });
});
