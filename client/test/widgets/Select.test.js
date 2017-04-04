import {expect} from 'chai';
import {jsx, List} from 'view-utils';
import sinon from 'sinon';
import {createMockComponent, mountTemplate, selectByText, triggerEvent} from 'testHelpers';
import {Select, Option, __set__, __ResetDependency__} from 'widgets/Select';

describe('<Select>', () => {
  let update;
  let Dropdown;
  let MockSocket;
  let onValueSelected;
  let dropdownListNode;
  let dropdownLabelNode;
  let state;

  beforeEach(() => {
    Dropdown = createMockComponent('Dropdown', true);
    __set__('Dropdown', Dropdown);

    MockSocket = createMockComponent('Socket', true);
    __set__('Socket', MockSocket);

    onValueSelected = sinon.spy();
    state = {};

    ({update} = mountTemplate(<Select onValueSelected={onValueSelected}>
      <Option value="ala" isDefault>Ala</Option>,
      <Option value="marcin">Marcin</Option>
    </Select>));
    update(state);

    dropdownListNode = MockSocket.getChildrenNode({name: 'list'});
    dropdownLabelNode = MockSocket.getChildrenNode({name: 'label'});
  });

  afterEach(() => {
    __ResetDependency__('Dropdown');
    __ResetDependency__('Socket');
    __ResetDependency__('withSockets');
  });

  it('should display all dropdown options', () => {
    expect(dropdownListNode).to.contain.text('Ala');
    expect(dropdownListNode).to.contain.text('Marcin');
  });

  it('should select default option on startup', () => {
    expect(dropdownLabelNode).to.contain.text('Ala');
  });

  it('should be able to select element by clicking option', () => {
    const [marcinOption] = selectByText(
      dropdownListNode.querySelectorAll('a'),
      'Marcin'
    );

    triggerEvent({
      node: marcinOption,
      eventName: 'click'
    });

    update();

    expect(dropdownLabelNode).to.contain.text('Marcin');
    // The second argument passed to onValueSelected is state at the moment
    // of triggering event, not last update. Which is just how
    // it supposed to be, but can be confusing it this test case.
    expect(onValueSelected.calledWith({name: 'Marcin', value: 'marcin'}, state))
      .to.eql(true, 'expect onValueSelect to be called with proper item');
  });

  describe('when more than one <Select> used', () => {
    let node;
    let firstOnValue;
    let secondOnValue;

    beforeEach(() => {
      firstOnValue = sinon.spy();
      secondOnValue = sinon.spy();

      ({update, node} = mountTemplate(
        <div>
          <Select onValueSelected={firstOnValue}>
            <Option value="a" isDefault>A</Option>
            <Option value="b">B</Option>
          </Select>
          <Select onValueSelected={secondOnValue}>
            <Option value="a2" isDefault>A2</Option>
            <Option value="b2">B2</Option>
          </Select>
        </div>
      ));
    });

    it('should have correctly set default values', () => {
      expect(firstOnValue.calledWith({name: 'A', value:'a'})).to.eql(true);
      expect(secondOnValue.calledWith({name: 'A2', value:'a2'})).to.eql(true);
    });

    it('should be able to use only first select', () => {
      secondOnValue.reset();
      const [bOption] = selectByText(
        node.querySelectorAll('a'),
        'B'
      );

      triggerEvent({
        node: bOption,
        eventName: 'click'
      });

      expect(firstOnValue.calledWith({name: 'B', value:'b'})).to.eql(true);
      expect(secondOnValue.called).to.eql(false);
    });

    it('should be able to use only second select', () => {
      firstOnValue.reset();
      const [bOption] = selectByText(
        node.querySelectorAll('a'),
        'B2'
      );

      triggerEvent({
        node: bOption,
        eventName: 'click'
      });

      expect(secondOnValue.calledWith({name: 'B2', value:'b2'})).to.eql(true);
      expect(firstOnValue.called).to.eql(false);
    });
  });

  describe('when one <Select> component is used multiple times', () => {
    let node;
    let state;
    let firstSelectNode;
    let secondSelectNode;

    beforeEach(() => {
      state = [2, 3];

      ({node, update} = mountTemplate(<List>
        <div class="element">
          <Select onValueSelected={(item) => {}}>
            <Option value="d" isDefault>def</Option>
            <Option value="d2">dd</Option>
          </Select>
        </div>
      </List>));

      update(state);

      ([firstSelectNode, secondSelectNode] = node.querySelectorAll('.element'));
    });

    it('both select usages should have default values set', () => {
      expect(firstSelectNode).to.contain.text('def');
      expect(secondSelectNode).to.contain.text('def');
    });

    it('should be possible to use one select without changing other', () => {
      const [ddOption] = selectByText(
        firstSelectNode.querySelectorAll('a'),
        'dd'
      );

      triggerEvent({
        node: ddOption,
        eventName: 'click'
      });

      expect(firstSelectNode).to.contain.text('dd');
      expect(secondSelectNode).to.contain.text('def');
    });
  });
});
