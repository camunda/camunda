import {jsx, withSelector, withSockets, Children} from 'view-utils';
import {runOnce} from 'utils';
import {Filter, getFilter} from './filter';
import {View, getView} from './view';

export const Controls = withSockets(withSelector(({onCriteriaChanged, getBpmnViewer, getProcessDefinition, sockets: {head, body}}) => {
  const template = <div className="controls row">
    <div className="col-xs-12">
      <form>
        <table>
          <thead>
            <tr>
              <td><label>View</label></td>
              <td colspan="2"><label>Filter</label></td>
              <Children children={head} />
            </tr>
          </thead>
          <tbody>
            <tr>
              <View onViewChanged={onViewChanged}/>
              <Filter onFilterChanged={onControlsChange} getProcessDefinition={getProcessDefinition} />
              <Children children={body} />
            </tr>
          </tbody>
        </table>
      </form>
    </div>
  </div>;

  function onViewChanged(view) {
    onCriteriaChanged({
      query: getFilter(),
      view
    });
  }

  function onControlsChange() {
    onCriteriaChanged({
      query: getFilter(),
      view: getView()
    });
  }

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, runOnce(onControlsChange)];
  };
}));
