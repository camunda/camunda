import {jsx, withSelector, Children} from 'view-utils';
import {runOnce} from 'utils';
import {Filter, getFilter} from './filter';
import {View, getView} from './view';

export const Controls = withSelector(({onCriteriaChanged, getBpmnViewer, getProcessDefinition, children}) => {
  const template = <div className="controls">
    <View onViewChanged={onViewChanged}/>
    <Filter onFilterChanged={onControlsChange} getProcessDefinition={getProcessDefinition} />
    <Children children={children} />
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
});
