import {
  jsx, withSockets, Socket, Children, $document, Scope, Match, Case, Default,
  createReferenceComponent
} from 'view-utils';
import {Dropdown, DropdownItem} from './Dropdown';

const SELECT_EVENT = 'SELECT_EVENT';

export const Select = withSockets(({onValueSelected, sockets: {label, list}}) => {
  const Reference = createReferenceComponent();
  let currentItem;

  const template = <Dropdown>
    <Socket name="label">
      <Match>
        <Case predicate={() => label}>
          <Scope selector={addCurrentItemToState}>
            <Children children={label} />
          </Scope>
        </Case>
        <Default>
          <span>
            <Reference name="label" />
          </span>
        </Default>
      </Match>
    </Socket>
    <Socket name="list">
      <Children children={list} />
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

    if (!label && labelNode && text && labelNode.innerText !== text) {
      labelNode.innerText = text;
    }
  }

  function addCurrentItemToState(state) {
    return {
      ...state,
      current: currentItem
    };
  }
});

export function StaticOption({name, value, isDefault = false}) {
  const Reference = createReferenceComponent();
  const template = <DropdownItem listener={selectValue}>
    <Reference name="eventSource" />
    {name}
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

    event.initEvent(SELECT_EVENT, true, true, null);
    event.item = {name, value};

    node.dispatchEvent(event);
  }
}
