# Generating SVG components

We use `@svgr/cli` to convert SVG files into reusable React components, simplifying their integration into the project.

Run the following command inside an apps client directory to generate React components from the SVG source files:

```sh
npm run generate:svg
```

This command processes all SVG files in `src/assets/svg/` and generates React components from them. These components are stored in `src/modules/svg/` and are intended for use throughout the project. They must not be edited manually, as any changes will be overwritten the next time the command is run.

This must be run manually in the following cases:

- **A new SVG is added** to `src/assets/svg/`
- **An existing SVG is changed** in `src/assets/svg/`
