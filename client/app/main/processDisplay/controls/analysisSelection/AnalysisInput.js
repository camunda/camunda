import {jsx, OnEvent, Match, Case, Default, Text, withSelector, Children} from 'view-utils';

export const AnalysisInput = withSelector(({children}) => {
  return <td>
    <ul className="list-group">
      <li className="list-group-item" style="padding: 6px; cursor: default;">
        <OnEvent event="mouseover" listener={hover} />
        <OnEvent event="mouseout" listener={unhover} />
        <Match>
          <Case predicate={isSelected}>
            <span>
              <button type="button" className="btn btn-link btn-xs pull-right" style="padding-top: 0; padding-bottom: 0;">
                <OnEvent event="click" listener={unset} />
                ×
              </button>
              <Text property="name" />
            </span>
          </Case>
          <Default>
            <span>Please Select <Text property="label" /></span>
          </Default>
        </Match>
        <Children children={children} />
      </li>
    </ul>
  </td>;

  function isSelected({name}) {
    return name;
  }

  function unset({state:{integrator, type}}) {
    integrator.unset(type);
  }

  function hover({state:{integrator, type}}) {
    integrator.hover(type, true);
  }

  function unhover({state:{integrator, type}}) {
    integrator.unhover(type, true);
  }
});
