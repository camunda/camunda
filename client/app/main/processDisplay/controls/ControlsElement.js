import {jsx, Children} from 'view-utils';

export function ControlsElement({name, children}) {
  return <div className="controls-element">
    <div className="controls-element-name">{name}</div>
    <div className="controls-element-body">
      <Children children={children} />
    </div>
  </div>;
}
