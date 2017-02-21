import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {createStateComponent, jsx} from 'view-utils';

describe('createStateComponent', () => {
  const text = 'some text';
  let State;
  let node;
  let update;

  beforeEach(() => {
    State = createStateComponent();

    ({node, update} = mountTemplate(
      <State>{text}</State>
    ));
  });

  it('should have children', () => {
    expect(node).to.contain.text(text);
  });

  describe('getState', () => {
    it('should return last state passed to update', () => {
      const state = 'some state';

      update(state);

      expect(State.getState()).to.equal(state);
    });
  });
});
