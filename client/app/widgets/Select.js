import {jsx, Socket, Children, $document, createReferenceComponent, OnEvent, Attribute} from 'view-utils';
import {Dropdown, DropdownItem} from './Dropdown';
import isEqual from 'lodash.isequal';

const SELECT_EVENT = 'SELECT_EVENT';

export function Select({onValueSelected, getSelectValue, children}) {
  return (node, eventsBus) => {
    const Reference = createReferenceComponent();

    // Template is split into two parts to solve problems with components application
    // Event listener on select node must be added before <Option> component in children
    // are applied to node. And the easiest solution is just to split template into two parts.
    // So it might seem a little bit strange, but there is good reason for it.
    const template = <div>
      <Reference name="select" />
      <OnEvent event={SELECT_EVENT} listener={onSelection} />
    </div>;

    const dropdownTemplate = <Dropdown>
      <Socket name="label">
        <span>
          <Reference name="label" />
        </span>
      </Socket>
      <Socket name="list">
        <Reference name="list" />
        <Children children={children} />
      </Socket>
    </Dropdown>;

    let currentItem;

    const templateUpdate = template(node, eventsBus);
    const selectNode = Reference.getNode('select');

    return [
      templateUpdate,
      updateLabelNode,
      dropdownTemplate(selectNode, eventsBus)
    ];

    function onSelection({event: {item}, state}) {
      currentItem = item;

      onValueSelected(item, state);
    }

    function updateLabelNode() {
      if (typeof getSelectValue === 'function') {
        currentItem = getSelectItem() || currentItem;
      }

      const labelNode = Reference.getNode('label');
      const text = currentItem && currentItem.name ? currentItem.name : currentItem;

      if (labelNode && text && labelNode.innerText !== text) {
        labelNode.innerText = text;
      }
    }

    function getSelectItem() {
      const value = getSelectValue();
      const valueNodes = Reference.getNode('list').querySelectorAll('[select-value]');
      const [valueNode] = Array.prototype.filter.call(valueNodes, (valueNode) => {
        const valueAttribute = valueNode.getAttribute('select-value');

        return isEqual(JSON.parse(valueAttribute), value);
      });

      if (valueNode) {
        return {
          value,
          name: valueNode.innerText.trim()
        };
      }
    }
  };
}

export function Option({children, value, isDefault = false}) {
  return (node, eventsBus) => {
    const Reference = createReferenceComponent();
    const template = <DropdownItem listener={selectValue}>
      <Reference name="eventSource" />
      <Attribute selector={() => JSON.stringify(value)} attribute="select-value" />
      <Children children={children} />
    </DropdownItem>;
    const templateUpdate = template(node, eventsBus);

    if (isDefault) {
      selectValue({node: Reference.getNode('eventSource')});
    }

    return templateUpdate;
  };

  function selectValue({node}) {
    const event = $document.createEvent('CustomEvent');
    const name = node.innerText.trim();

    event.initEvent(SELECT_EVENT, true, true, null);
    event.item = {name, value};

    node.dispatchEvent(event);
  }
}
