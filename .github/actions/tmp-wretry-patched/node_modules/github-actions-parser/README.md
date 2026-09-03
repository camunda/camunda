# github-actions-parser

![CI](https://github.com/cschleiden/github-actions-parser/workflows/CI/badge.svg)

This package provides a parser and various language-server related features for GitHub Actions.

Auto-completion and validation of expressions (e.g., `${{ secrets.FOO }}`) requires information about the repository that contains the workflow as well as an authenticated [Octokit](https://octokit.github.io/rest.js/v18/) client for making API calls.

## Usage

Some usages examples:

### Parse a workflow

```ts
// Workflow text
const input = "on: ..."

const workflowDoc = await parse({
  client, // Authenticated Octokit client. Needs at least `repo`, `actions`, and for org-secrets also org admin permissions
  owner: "repository-owner",
  repository: "repository"
}, input)

// workflowDoc.workflow // Parsed, normalized workflow
// workflowDoc.diagnostics // Errors/warnings
// workflowDoc.workflowST // AST
```

### Auto-complete

tbd

### Get "hover" information

tbd

### Evaluate an expression

```ts
const result = evaluateExpression("${{ env.TEST == 42 }}, {
  get(contextName: "github" | "env" | ...) {
    if (contextName === "env) {
      return {
        TEST: 42
      };
    }

    return {};
  }
}) // Evaluates to true
```

## Structure

- `lib/expressions` - Parser and evaluator for GitHub Actions workflow expressions
- `lib/parser` - Generic YAML parser with validation and auto-complete support
- `lib/parser/schema` - TypeScript based YAML schema
- `lib/workflowschema` - GitHub Actions workflow specific parse/complete/hover functions, this is the main exported functionality

## Caching

In order to auto-complete and validate parts of the workflow depending on the state of other repositories (Actions being used) or some data in the repository the parser needs to make API calls. The results of the API calls are cached for some time to avoid making too many calls. The cache lives in the imported module, which works well for VS Code, for example, where switching repositories means reloading the editor but could lead to issues if the same instance of the module is used for different repositories.

## Acknowledgements

This library is built on some great open-source projects:

- [octokit/rest](https://octokit.github.io/rest.js/v18/) for making API calls to GitHub
- [js-yaml](https://github.com/nodeca/js-yaml) for general parsing (e.g., `action.yml` files for `inputs` auto-completion)
- [yaml-ast-parser](https://github.com/mulesoft-labs/yaml-ast-parser) for parsing the workflow into an AST for validation/auto-completion
- [chevrotain](https://github.com/SAP/chevrotain) for parsing and running expressions
