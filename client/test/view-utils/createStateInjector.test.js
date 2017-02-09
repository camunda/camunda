import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {createStateInjector, jsx} from 'view-utils';

describe('createStateInjector', () => {
  const text = 'some text';
  let StateInjector;
  let node;
  let update;

  beforeEach(() => {
    StateInjector = createStateInjector();

    ({node, update} = mountTemplate(
      <StateInjector>{text}</StateInjector>
    ));
  });

  it('should have children', () => {
    expect(node).to.contain.text(text);
  });

  describe('getState', () => {
    it('should return last state passed to update', () => {
      const state = 'some state';

      update(state);

      expect(StateInjector.getState()).to.equal(state);
    });
  });
});
