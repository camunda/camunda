import {jsx, withSelector} from 'view-utils';
import {loadData, loadDiagram, getDefinitionId} from './service';
import {isViewSelected, Controls} from './controls';
import {resetStatisticData, ViewsDiagramArea, ViewsArea} from './views';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const template = <div className="process-display">
    <Controls selector={createControlsState} onCriteriaChanged={handleCriteriaChange} getProcessDefinition={getDefinitionId} >
      <ViewsArea areaComponent="Controls" selector="views" isViewSelected={isViewSelected} />
    </Controls>
    <ViewsDiagramArea selector="views" isViewSelected={isViewSelected} />
    <ViewsArea areaComponent="Additional" selector="views" isViewSelected={isViewSelected} />
  </div>;

  function handleCriteriaChange(newCriteria) {
    resetStatisticData();
    loadData(newCriteria);
  }

  function createControlsState({controls, views}) {
    return {
      ...controls,
      views
    };
  }

  return (node, eventsBus) => {
    loadDiagram();

    return template(node, eventsBus);
  };
}
