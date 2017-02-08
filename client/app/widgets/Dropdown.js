import {jsx, Children, createReferenceComponent, withSockets} from 'view-utils';
import $ from 'jquery';

export const Dropdown = withSockets(({children, sockets: {label, list}}) => {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);

  const template = <div className="btn-group dropdown">
    <button type="button" className="btn btn-default dropdown-toggle" data-toggle="dropdown">
      <Reference name="dropdown" />
      <Children children={label} />
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

export function DropdownItem({children}) {
  return <li>
    <a href="#">
      <Children children={children} />
    </a>
  </li>;
}
