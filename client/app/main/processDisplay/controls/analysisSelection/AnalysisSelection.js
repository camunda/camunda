import {jsx, withSelector, Children, createReferenceComponent} from 'view-utils';
import {AnalysisInput} from './AnalysisInput';

export function createAnalysisSelection(integrator) {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);

  const AnalysisSelection = withSelector(() => {
    return <Children>
      <AnalysisInput selector={formatSelection('endEvent')} integrator={integrator}>
        <Reference name="EndEvent" />
      </AnalysisInput>
      <AnalysisInput selector={formatSelection('gateway')} integrator={integrator}>
        <Reference name="Gateway" />
      </AnalysisInput>
    </Children>;

    function formatSelection(type) {
      return (selection) => {
        return {
          type: capitalize(type),
          label: capitalize(type.replace(/[A-Z]/g, ' $&')),
          name: selection[type]
        };
      };
    }
  });

  AnalysisSelection.nodes = nodes;

  return AnalysisSelection;
}

function capitalize(string) {
  return string.replace(/^./g, firstLetter => firstLetter.toUpperCase());
}
