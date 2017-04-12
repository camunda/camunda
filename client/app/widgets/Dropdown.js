import {jsx, Children, OnEvent, createReferenceComponent, withSockets} from 'view-utils';
import $ from 'jquery';

export const Dropdown = withSockets(({sockets: {label, list}}) => {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);

  const template = <div className="btn-group dropdown">
    <button type="button" className="btn btn-default dropdown-toggle" data-toggle="dropdown">
      <Reference name="dropdown" />
      <Children children={label} />
      {' '}<span className="caret"></span>
    </button>
    <ul className="dropdown-menu">
      <Children children={list} />
    </ul>
  </div>;

  return (parentNode, eventsBus) => {
    const update = template(parentNode, eventsBus);

    $(nodes.dropdown).dropdown();

    return update;
  };
});

export function DropdownItem({children, listener}) {
  return <li>
    <a href="#">
      <OnEvent event="click" listener={onClick} />
      <Children children={children} />
    </a>
  </li>;

  function onClick(params) {
    params.event.preventDefault();

    listener(params);
  }
}
