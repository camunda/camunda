import {expect} from 'chai';
import sinon from 'sinon';
import {jsx, List, Text, Match, Case, Default, DESTROY_EVENT} from 'view-utils';
import {createMockComponent, mountTemplate} from 'testHelpers';

describe('<List>', () => {
  const childClass = 'child-class';
  const key = 'key';
  let Child;
  let node;
  let update;
  let values;

  beforeEach(() => {
    Child = createMockComponent('child');

    ({node, update} = mountTemplate(<List key={key}>
      key-<Text property="key" />
      <div className={childClass}>
        order-<Text property="key" />
        <Child/>
      </div>
    </List>));

    values = [
      {
        [key]: 1
      },
      {
        [key]: 2
      }
    ];
  });

  it('should append LIST START comment to mark start point of list', () => {
    const hasStartComment = Array.prototype.some.call(
      node.childNodes,
      ({textContent}) => textContent === 'LIST START'
    );

    expect(hasStartComment).to.eql(true);
  });

  it('should not append any child before update', () => {
    expect(node.querySelectorAll(`.${childClass}`)).not.to.exist;
  });

  it('should throw error when keys for list are not unique', () => {
    const values = [
      {
        [key]: 1
      },
      {
        [key]: 1
      }
    ];

    expect(() => update(values)).to.throw;
  });

  describe('after update', () => {
    beforeEach(() => {
      update(values);
    });

    it('should create child for each value', () => {
      expect(node.querySelectorAll(`.${childClass}`).length).to.eql(values.length);
      expect(node).to.contain.text('key-1');
      expect(node).to.contain.text('key-2');
    });

    it('should keep order of children', () => {
      const children = node.querySelectorAll(`.${childClass}`);

      expect(children[0]).to.contain.text('order-1');
      expect(children[1]).to.contain.text('order-2');
    });

    it('should update children', () => {
      expect(Child.mocks.update.callCount).to.eql(2);

      expect(Child.mocks.update.getCall(0).args).to.eql([values[0]]);
      expect(Child.mocks.update.getCall(1).args).to.eql([values[1]]);
    });

    describe('after more updates', () => {
      let otherValues;

      beforeEach(() => {
        otherValues = [
          {
            [key]: 2
          },
          {
            [key]: 3
          }
        ];
      });

      it('should delete unused children after another update', () => {
        Child.mocks.template.reset();
        update(otherValues);

        expect(node).not.to.contain.text('key-1');
        expect(node).to.contain.text('key-2');
        expect(node).to.contain.text('key-3');
      });

      it('should create reuse children templates', () => {
        Child.mocks.template.reset();
        update(otherValues);

        expect(Child.mocks.template.called).to.eql(false);
      });

      it('should add only one child when called with 1 more value', () => {
        const values = otherValues.concat({
          [key]: 4
        });

        Child.mocks.template.reset();
        update(values);

        expect(Child.mocks.template.calledOnce).to.eql(true);
      });

      it('should pass destroy event to unnecessary children', () => {
        const firstChildEventsBus = Child.getEventsBus(0);
        const secondChildEventsBus = Child.getEventsBus(1);
        const firstListener = sinon.spy();
        const secondListener = sinon.spy();
        const newValues = values.slice(1);

        firstChildEventsBus.on(DESTROY_EVENT, firstListener);
        secondChildEventsBus.on(DESTROY_EVENT, secondListener);

        update(newValues);

        expect(firstListener.calledOnce).to.eql(true);
        expect(secondListener.called).to.eql(false);
      });
    });
  });

  describe('reusing nodes', () => {
    let SecondChild;
    let otherValues;

    beforeEach(() => {
      SecondChild = createMockComponent('second-child');

      ({node, update} = mountTemplate(<List key={key}>
        <div className={childClass}>
          <Match>
            <Case predicate={({key}) => key % 2 === 1}>
              key-<Text property="key" />
              <Child/>
            </Case>
            <Default>
              skey-<Text property="key" />
              <SecondChild/>
            </Default>
          </Match>
        </div>
      </List>));

      update(values);

      otherValues = [
        {
          [key]: 2,
          d: 1
        },
        {
          [key]: 3
        }
      ];

      update(otherValues);
    });

    it('should reuse second child', () => {
      expect(SecondChild.mocks.template.calledOnce).to.eql(true, 'expected only one instance of second child');
      expect(SecondChild.mocks.update.getCall(0).args).to.eql([values[1]]);
      expect(SecondChild.mocks.update.getCall(1).args).to.eql([otherValues[0]]);
    });

    it('should reuse first child', () => {
      expect(Child.mocks.template.calledOnce).to.eql(true, 'expected only one instance of second child');
      expect(Child.mocks.update.getCall(0).args).to.eql([values[0]]);
      expect(Child.mocks.update.getCall(1).args).to.eql([otherValues[1]]);
    });
  });
});
