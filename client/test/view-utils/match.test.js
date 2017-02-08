import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {jsx, Text, DESTROY_EVENT} from 'view-utils';
import {Match, Case} from 'view-utils';

describe('<Match>', () => {
  const key = 'key';
  let FirstChild;
  let SecondChild;
  let update;
  let node;

  beforeEach(() => {
    FirstChild = createMockComponent('first-child');
    SecondChild = createMockComponent('second-child');

    ({node, update} = mountTemplate(<Match>
      <Case predicate={state => state[key] % 2 === 1}>
        t1 = <Text property={key} />
        <FirstChild/>
      </Case>
      <Case predicate={state => state[key] % 2 === 0}>
        t2 = <Text property={key} />
        <SecondChild/>
      </Case>
    </Match>));
  });

  it('should not display add any child before update', () => {
    expect(FirstChild.mocks.template.called).to.eql(false);
    expect(SecondChild.mocks.template.called).to.eql(false);
  });

  it('should add comment node as start marker', () => {
    expect(node.childNodes[0].textContent).to.eql('START MATCH');
  });

  it('should display FirstChild when first predicate is true', () => {
    const state = {
      [key]: 1
    };

    update(state);

    expect(node).to.contain.text('t1 = 1');
    expect(FirstChild.mocks.template.calledOnce).to.eql(true);
    expect(FirstChild.mocks.update.calledWith(state)).to.eql(true);
  });

  it('should display SecondChild when second predicate is true', () => {
    const state = {
      [key]: 2
    };

    update(state);

    expect(node).to.contain.text('t2 = 2');
    expect(SecondChild.mocks.template.calledOnce).to.eql(true);
    expect(SecondChild.mocks.update.calledWith(state)).to.eql(true);
  });

  it('should send destroy event to child when it is being replaced', () => {
    const listener = sinon.spy();
    const state1 = {
      [key]: 1
    };

    update(state1);

    const childEventsBus = FirstChild.getEventsBus(0);

    childEventsBus.on(DESTROY_EVENT, listener);

    const state2 = {
      [key]: 2
    };

    update(state2);

    expect(listener.calledOnce).to.eql(true);
  });

  it('should update child with new state if predicate is still true', () => {
    const state1 = {
      [key]: 2
    };
    const state2 = {
      [key]: 4
    };

    update(state1);
    update(state2);

    expect(SecondChild.mocks.template.calledOnce).to.eql(true);
    expect(SecondChild.mocks.update.getCall(0).args).to.eql([state1]);
    expect(SecondChild.mocks.update.getCall(1).args).to.eql([state2]);
  });

  it('should remove all nodes of previous child', () => {
    const state1 = {
      [key]: 1
    };
    const state2 = {
      [key]: 2
    };

    update(state1);
    update(state2);

    expect(node).to.not.contain.text('t1');
    expect(node).to.not.contain.text('1');
    expect(node).to.not.contain.text('first-child');
  });
});
