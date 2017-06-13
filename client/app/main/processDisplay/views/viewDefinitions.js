import {definition as analyticsDefinition} from './analytics';
import {frequencyDefinition, durationDefinition} from './heatmap';
import {definition as targetValueDefinition} from './targetValueDisplay';

export const definitions = {
  'branch_analysis': analyticsDefinition,
  frequency: frequencyDefinition,
  duration: durationDefinition,
  'target_value': targetValueDefinition
};
