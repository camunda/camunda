export function execute(variables, context) {
  return { text: `Hello ${variables.name}, the sum is ${variables.sum} (${context.elementId})` };
}
