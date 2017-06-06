import {expect} from 'chai';
import {reducer, createSetTargetValueAction, SET_TARGET_VALUE} from 'main/processDisplay/views/targetValueDisplay/reducer';

describe('target value reducer', () => {
  const ELEMENT_ID = 'ELEMENT_ID';
  const VALUE = 123;
  let element;

  beforeEach(() => {
    element = {
      businessObject: {
        id: ELEMENT_ID
      }
    };
  });

  it('should create an action', () => {
    const action = createSetTargetValueAction(element, VALUE);

    expect(action.type).to.eql(SET_TARGET_VALUE);
    expect(action.element).to.eql(ELEMENT_ID);
    expect(action.value).to.eql(VALUE);
  });

  it('should store target value in data object', () => {
    const state = reducer(undefined, createSetTargetValueAction(element, VALUE));

    expect(state.data[ELEMENT_ID]).to.eql(VALUE);
  });

  it('should remove a value when it is set to 0', () => {
    const state = reducer({data: {
      [ELEMENT_ID]: VALUE
    }}, createSetTargetValueAction(element, 0));

    expect(state.data[ELEMENT_ID]).to.not.exist;
  });
});
