import {jsx, Socket, Children, $document, createReferenceComponent} from 'view-utils';
import {Dropdown, DropdownItem} from './Dropdown';

const SELECT_EVENT = 'SELECT_EVENT';

export function Select({onValueSelected, children}) {
  const Reference = createReferenceComponent();
  let currentItem;

  const template = <Dropdown>
    <Socket name="label">
      <span>
        <Reference name="label" />
      </span>
    </Socket>
    <Socket name="list">
      <Children children={children} />
    </Socket>
  </Dropdown>;

  return (node, eventsBus) => {
    node.addEventListener(SELECT_EVENT, ({item}) => {
      currentItem = item;

      onValueSelected(item);
    });

    return [
      template(node, eventsBus),
      updateLabelNode
    ];
  };

  function updateLabelNode() {
    const labelNode = Reference.getNode('label');
    const text = currentItem && currentItem.name ? currentItem.name : currentItem;

    if (labelNode && text && labelNode.innerText !== text) {
      labelNode.innerText = text;
    }
  }
}

export function Option({children, value, isDefault = false}) {
  const Reference = createReferenceComponent();
  const template = <DropdownItem listener={selectValue}>
    <Reference name="eventSource" />
    <Children children={children} />
  </DropdownItem>;

  return (node, eventsBus) => {
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
