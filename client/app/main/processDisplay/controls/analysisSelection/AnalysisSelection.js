import {jsx, withSelector, Children, createReferenceComponent} from 'view-utils';
import {AnalysisInput} from './AnalysisInput';

export function createAnalysisSelection(integrator) {
  const nodes = {};
  const Reference = createReferenceComponent(nodes);

  const AnalysisSelection = withSelector(() => {
    return <Children>
      <AnalysisInput selector={exposeIntegrator('endEvent')}>
        <Reference name="EndEvent" />
      </AnalysisInput>
      <AnalysisInput selector={exposeIntegrator('gateway')}>
        <Reference name="Gateway" />
      </AnalysisInput>
    </Children>;

    function exposeIntegrator(type) {
      return (selection) => {
        return {
          integrator,
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
