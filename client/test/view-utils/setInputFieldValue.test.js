import {expect} from 'chai';
import {jsx, setInputValue, SetInputFieldValue} from 'view-utils';
import {mountTemplate} from 'testHelpers';

describe('<SetInputValue>', () => {
  const selectionStart = 2;
  const selectionEnd = 5;
  const selectionDirection = 'forward';
  let node;
  let input;
  let state;
  let update;

  beforeEach(() => {
    ({node, update} = mountTemplate(<input type="text">
      <SetInputFieldValue getValue="prop" />
    </input>));

    input = node.querySelector('input');
    input.value = 'longer text';
    input.selectionStart = selectionStart;
    input.selectionEnd = selectionEnd;
    input.selectionDirection = selectionDirection;

    state = {
      prop: 'value-3'
    };

    update(state);
  });

  it('should set value on input', () => {
    expect(input.value).to.eql(state.prop);
  });

  it('should preserve selection', () => {
    expect(input.selectionStart).to.eql(selectionStart);
    expect(input.selectionEnd).to.eql(selectionEnd);
    expect(input.selectionStart).to.eql(selectionStart);
  });
});

describe('setInputValue', () => {
  const selectionStart = 2;
  const selectionEnd = 5;
  const selectionDirection = 'forward';
  let input;
  let value;

  beforeEach(() => {
    input = {
      selectionStart,
      selectionEnd,
      selectionDirection,
      _value: undefined
    };

    Object.defineProperty(input, value, {
      get: () => input._value,
      set: (value) => {
        input.selectionStart = 0;
        input.selectionEnd = 0;
        delete input.selectionDirection;

        input._value = value;
      }
    });

    value = 'new-value';

    setInputValue(input, value);
  });

  it('should set value on input', () => {
    expect(input.value).to.eql(value);
  });

  it('should preserve selection properties', () => {
    expect(input.selectionDirection).to.eql(selectionDirection);
    expect(input.selectionStart).to.eql(selectionStart);
    expect(input.selectionEnd).to.eql(selectionEnd);
  });
});
