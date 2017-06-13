import {jsx, Children} from 'view-utils';
import {AnalysisInput} from './AnalysisInput';

export function createAnalysisSelection(getNameForElement) {
  const AnalysisSelection = () => {
    return <Children>
      <AnalysisInput name="End Event" selector={formatSelection('EndEvent')} />
      <AnalysisInput name="Gateway" selector={formatSelection('Gateway')} />
    </Children>;

    function formatSelection(type) {
      return ({selection, hover}) => {
        return {
          type: capitalize(type),
          label: capitalize(type.replace(/[A-Z]/g, ' $&')),
          name: getNameForElement(selection[type]),
          hovered: hover[type]
        };
      };
    }
  };

  return AnalysisSelection;
}

function capitalize(string) {
  return string.replace(/^./g, firstLetter => firstLetter.toUpperCase());
}
