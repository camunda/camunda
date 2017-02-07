import {expect} from 'chai';
import sinon from 'sinon';
import {jsx} from 'view-utils';
import {setupPromiseMocking, mountTemplate, createMockComponent} from 'testHelpers';
import {DynamicLoader, __set__, __ResetDependency__} from 'dynamicLoader/component';

describe('<DynamicLoader>', () => {
  const module = 'module';
  const prop = 'prop1';
  let dispatchAction;
  let TargetComponent;
  let getModule;
  let node;
  let update;
  let eventsBus;

  setupPromiseMocking();

  beforeEach(() => {
    dispatchAction = sinon.spy();
    __set__('dispatchAction', dispatchAction);

    TargetComponent = createMockComponent('child');

    getModule = sinon.stub().returns(
      Promise.resolve({
        component: TargetComponent
      })
    );
    __set__('getModule', getModule);

    ({node, update, eventsBus} = mountTemplate(<DynamicLoader module={module} prop={prop} />));
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('getModule');
  });

  it('should add empty div', () => {
    expect(node.children.length).to.eql(1, 'expected only one child to be added to parent node');
    expect(node.children[0].tagName.toLowerCase()).to.eql('div', 'expected child of parent node to be div');
  });

  it('should set loader loading classes on div', () => {
    const div = node.children[0];

    expect(div).to.have.class('loader');
    expect(div).to.have.class('loading');
  });

  it('should load module with component', () => {
    expect(getModule.calledWith(module)).to.eql(true);
  });

  describe('on module loaded', () => {
    beforeEach(() => {
      Promise.runAll();
    });

    it('should render target component', () => {
      expect(node).to.contain.text('child');
    });

    it('should call target component update with state on dynamic loader update', () => {
      const state = 'state';

      update(state);

      expect(TargetComponent.mocks.update.calledWith(state)).to.eql(true);
    });

    it('should dispatch @@LOADED action', () => {
      expect(dispatchAction.calledWith({
        type: '@@LOADED',
        module
      })).to.eql(true);
    });

    it('should remove loader and loading classes from div', () => {
      const div = node.children[0];

      expect(div).not.to.have.class('loader');
      expect(div).not.to.have.class('loading');
    });

    it('should pass event to target component', () => {
      const event = 'event-1';
      const data = 'data-2';
      const listener = sinon.spy();
      const targetEventsBus = TargetComponent.getEventsBus(0);

      targetEventsBus.on(event, listener);

      eventsBus.fireEvent(event, data);

      expect(listener.calledWith({
        name: event,
        data,
        stopped: false
      })).to.eql(true);
    });
  });

  describe('on failed module loading', () => {
    const error = 'some error';

    beforeEach(() => {
      getModule.returns(
        Promise.reject(error)
      );

      ({node} = mountTemplate(<DynamicLoader module={module} prop={prop} />));
    });

    it('should throw error', () => {
      expect(() => {
        Promise.runAll();
      }).to.throw;
    });

    it('should display error message', () => {
      try {
        Promise.runAll();
      } catch (err) {
        //Ignore error
      }

      expect(node).to.contain.text(`Could not load ${module} module`);
    });
  });
});
