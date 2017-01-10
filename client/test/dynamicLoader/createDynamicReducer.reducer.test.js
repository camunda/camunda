import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {createDynamicReducer, __set__, __ResetDependency__} from 'dynamicLoader/createDynamicReducer.reducer';

describe('createDynamicReducer', () => {
  const module = 'mod-1';
  const prop = 'prop1';
  let onModuleLoaded;
  let targetReducer;
  let reducer;

  setupPromiseMocking();

  beforeEach(() => {
    targetReducer = sinon.stub().returns({prop});

    onModuleLoaded = sinon.stub().returns(
      Promise.resolve({
        reducer: targetReducer
      })
    );
    __set__('onModuleLoaded', onModuleLoaded);

    reducer = createDynamicReducer(module);
  });

  afterEach(() => {
    __ResetDependency__('onModuleLoaded');
  });

  it('should return state loading property set to true', () => {
    expect(reducer(
      undefined,
      {
        type: '@other'
      }
    )).to.eql({loading: true});
  });

  it('should not change other properties', () => {
    expect(reducer(
      {
        a: 1
      },
      {
        type: '@other'
      }
    )).to.eql({
      a: 1,
      loading: true
    });
  });

  it('should listen to module being loaded', () => {
    expect(onModuleLoaded.calledWith(module)).to.eql(true);
  });

  describe('after module is loaded', () => {
    beforeEach(() => {
      Promise.runAll();
    });

    it('should return state with loading set to false', () => {
      const {loading} = reducer({}, {
        type: '@@'
      });

      expect(loading).to.eql(false);
    });

    it('should call target reducer with state without loading property', () => {
      const state = {
        loading: true,
        a: 1
      };
      const action = {
        type: 'some-action'
      };

      reducer(state, action);

      expect(targetReducer.calledWith(
        {
          a: 1
        },
        action
      )).to.eql(true);
    });

    it('should merge properties returned by target reducer', () => {
      const {prop: actualProp} = reducer(undefined, {
        type: '@other-a'
      });

      expect(actualProp).to.eql(prop);
    });

    it('should preserve other properties', () => {
      const otherProp = 'other';
      const {otherProp: actualOtherProp} = reducer(
        {
          otherProp
        },
        {
          type: '@other-a'
        }
      );

      expect(actualOtherProp).to.eql(otherProp);
    });
  });
});
