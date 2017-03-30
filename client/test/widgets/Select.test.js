import {expect} from 'chai';
import {jsx} from 'view-utils';
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

  beforeEach(() => {
    Dropdown = createMockComponent('Dropdown', true);
    __set__('Dropdown', Dropdown);

    MockSocket = createMockComponent('Socket', true);
    __set__('Socket', MockSocket);

    onValueSelected = sinon.spy();
  });

  afterEach(() => {
    __ResetDependency__('Dropdown');
    __ResetDependency__('Socket');
    __ResetDependency__('withSockets');
  });

  describe('with default label', () => {
    beforeEach(() => {
      ({update} = mountTemplate(<Select onValueSelected={onValueSelected}>
        <Option value="ala" isDefault>Ala</Option>,
        <Option value="marcin">Marcin</Option>
      </Select>));
      update({});

      dropdownListNode = MockSocket.getChildrenNode({name: 'list'});
      dropdownLabelNode = MockSocket.getChildrenNode({name: 'label'});
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

      update({a: 1});

      expect(dropdownLabelNode).to.contain.text('Marcin');
      expect(onValueSelected.calledWith({name: 'Marcin', value: 'marcin'}))
        .to.eql(true, 'expect onValueSelect to be called with proper item');
    });
  });
});
