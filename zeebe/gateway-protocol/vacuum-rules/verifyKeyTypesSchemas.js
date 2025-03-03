function runRule(input) {
  // `input` is an entire schema object with all properties.
  //   Identify all properties that end with Key, and are not strings.
  return Object.entries(input)
    .filter(([name, schema]) => {
      return /Key$/.test(name) && schema.type !== "string";
    })
    .map((_) => {
      return {
        message: "`...Key` properties must be of type `string`.",
      };
    });
}
