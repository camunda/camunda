[![Stand With Ukraine](https://raw.githubusercontent.com/vshymanskyy/StandWithUkraine/main/banner2-direct.svg)](https://stand-with-ukraine.pp.ua)

# action::retry [![status](https://github.com/Wandalen/wretry.action/actions/workflows/wRetryActionPublish.yml/badge.svg)](https://github.com/Wandalen/wretry.action/actions/workflows/wRetryActionPublish.yml) [![stable](https://img.shields.io/badge/stability-stable-brightgreen.svg)](https://github.com/emersion/stability-badges#stable)

Retries a Github Action step or command on failure.

Works with either shell commands or other actions to retry.

## Why

Github actions which use the Internet connection can fail when connection is lost :

```bash
Run actions/setup-node@v1
connect ETIMEDOUT 104.20.22.46:443
Waiting 15 seconds before trying again
connect ETIMEDOUT 104.20.22.46:443
Waiting 18 seconds before trying again
Error: connect ETIMEDOUT 104.20.22.46:443
```

It is a cause of failed jobs. For this case, the action `wretry.action` can retry the action immediately after fail or with some delay. And if the connection will be restored, then the job will continue the normal run.

## Features

- Retries Github `JavaScript` actions.
- Retries `GitHub Docker` actions utilizing a `Dockerfile`'s and Docker registry image as the source.
- Retries private actions. The option `github_token` is used for private repositories.
- The action can be an action repository that is not published on `Marketplace`.
- Retries shell commands. Uses default shells to run commands.
- Can retry single action or single command ( multiline command ), but not both simultaneously.
- Retries `main`, `pre` and `post` stages of external actions.
- Default action always includes both `pre` and `post` stages. If an external action contains a `pre` and/or `post` stage, the action will also execute these stages.
- The repository includes subdirectories with alternative action setups that can skip the `pre` or/and `post` stages, as necessary.
- Action handles conditions in `JavaScript` and `Docker` actions ( fields `pre-if` and `post-if` ). Some conditions can be unsolvable and then action skips the stage.
- Resolves external action default inputs from next contexts : `github`, `env`, `job`, `matrix`, `steps`, `inputs`.
- Can resolve user-provided context `steps`.
- Retries actions with defined number of attempts ( default is 2 ).
- Retries actions with defined delay between attempts ( default is 0 ).

## Inputs

### `action`

The name of a Github action. Format is `{owner}/{repo_name}@{ref}`.

**Attention**. Action requires defined `action` or `command`. If the fields `action` and `commands` are defined simultaneously, then action will throw error.

### `command`

The command to run. The action runs the command in the default shell.

**Attention**. Action requires defined `action` or `command`. If the fields `action` and `commands` are defined simultaneously, then action will throw error.

### `pre_retry_command`

Command to run between retries.

### `with`

An options map for Github action. It is a multiline string with pairs `key : value`.

An example of declaration of option with single line value :
```yaml
- uses: Wandalen/wretry.action@master
  with:
    action: owner/action-repo@version
    with: |
      option1: value
      option2: value
```
An example of declaration of option with multiline string :
```yaml
- uses: Wandalen/wretry.action@master
  with:
    action: owner/action-repo@version
    with: |
      option1: |
        value1
        value2
          value3
      option2: value
```

### `current_path`

Setup working directory for the action. Works with only commands. Default is `github.workspace` path.

### `env_context`

Pass context `env` into an external action. Action cannot resolve separate environments of workflow at the startup and provide all environments. If you need valid context `env`, then add option `env_context : ${{ toJSON( env ) }}`.

### `github_context`

Pass context `github` into an external action. Default is global context `github`.

### `inputs_context`

Pass context `inputs` into an external action. The action cannot resolve context `inputs` and resolves all inputs from passed options and action description. To pass correct inputs context you need to combine passed options into and stringified JSON object.

### `job_context`

Pass context `job` into an external action. Default is context `job` of a job.

### `matrix_context`

Pass context `matrix` into an external action. Default is context `matrix` of a job.

### `steps_context`

Pass context `steps` into an external action. If you need valid context `steps`, then add option `steps_context : ${{ toJSON( steps ) }}`.

### `attempt_limit`

Set number of attempts. Default is 2.

### `attempt_delay`

Set delay between attempts in ms. Default is 0.

### `time_out`

Set time out in ms for entire step including all retries. By default actions sets no time out.

### `retry_condition`

Use any valid expression to control the continuation of retries. If the expression resolves to `false`, the action will interrupt the retries. Default value is `true`.
```yaml
- uses: Wandalen/wretry.action@master
  with:
    action: owner/action-repo@version
    retry_condition: github.ref_name == 'main'
    with: |
      option1: value
      option2: value
```
**Attention**. The expression can be wrapped in expression tokens `${{ <expr> }}`. The Github workflow runner will resolve the expressions wrapped in these tokens and replace the action input with the specific value. If you don't need the expression to be recalculated for each retry, you can put it inside the expression tokens.

**How to use outputs of current step in condition**

The action can resolve the output of the current step using the special syntax: `steps._this.outputs.<output_name>`. The action uses the special name `_this` to refer to the current step. If you have a step with the name `_this` and provide the steps context using the `steps_context` option, the action will rewrite the value of this field. This does not affect the outputs of the workflow, but allows the action to access the current step's outputs.

Example of condition with check of current step output:
```yaml
- uses: Wandalen/wretry.action@master
  with:
    action: owner/action-repo@version
    retry_condition: steps._this.outputs.code == 0
    with: |
      option1: value
      option2: value
```

**How to use complex conditions**

The library used for evaluating expressions does not account for operator precedence, which can lead to incorrect results in complex expressions where the order of operations is crucial.

Example of the issue. Consider the following expression:
```yaml
  'main' == 'main' && 'main' != 'master'
```

Due to the library's evaluation method, this expression is interpreted as:
```yaml
  (('main' == 'main') && 'main') != 'master'
```

As a result, it evaluates to `false`, which is not the intended outcome.

To ensure that the expression is evaluated correctly, you can use parentheses to explicitly define the order of operations. The corrected expression should be written as:
```yaml
  ('main' == 'main') && ('main' != 'master')
```

This adjustment clarifies the intended precedence and will yield the correct result.

### `github_token`

A token to access private actions. Does not required for public actions.

## Outputs

The action exposes single output named `outputs`. It collects all the outputs from the action/command in JSON map.

### How to use outputs from the external action

To access the value from an external action outputs parse the `wretry.action` output and select required key. To parse the outputs use builtin Github Actions function `fromJSON`.

Let's look at an example:
```yaml
jobs:
  job1:
    runs-on: ubuntu-latest
    outputs:
      # extract `outputs` from a step
      out: ${{ steps.my-action.outputs.outputs }}
    steps:
      - id: my-action
        uses: Wandalen/wretry.action@1.2.0
        with:
          attempt_limit: 3
          action: user/action@version
          with: |
            foo: bar
  job2:
    runs-on: ubuntu-latest
    needs: job1
    steps:
      - env:
          # parse full map and store it to OUPUT1
          OUTPUT1: ${{ fromJSON( needs.job1.outputs.out ) }}
          # parse full map and extract field `foo`
          OUTPUT2: ${{ fromJSON( needs.job1.outputs.out ).foo }}
        run: echo "$OUTPUT1 $OUTPUT2"
```

To setup job output we access output `outputs` of the step `my-action`. In the job `job2` we parse this output to JSON. The environment variable `OUPUT1` represents full JSON and the variable `OUPUT2` represents key `foo` of the parsed JSON.

## How to skip `pre` or/and `post` stages

The repository provides three subdirectories, each containing a different setup for action retries:

- `pre`: This directory retries the `pre` and `main` stages.
- `post`: This directory retries the `main` and `post` stages.
- `main`: This directory retries only the `main` stage.

It is crucial to note that, regardless of the retried action specification, the actions in the repository will only execute the declared stages. This behavior can disrupt your workflow, so please use the actions with caution.

### Selecting the Alternative Action

You have a few options for obtaining a compatible action implementation:
- Run the workflow with the required action, but without the `wretry.action`, and check the stages in the workflow run.
- Open the action directory and review the `action.yml` file. Look for any extra stages listed besides `main`.
- If you run command, then you can get `main` action that skips `pre` and `post` stages.

## Example usage

### Retry action
```yaml
- uses: Wandalen/wretry.action@master
  with:
    action: action/setup-node@2.3.0
    with: |
      node-version: 14.x
      architecture: x64
    attempt_limit: 3
    attempt_delay: 2000
```

### Retry command
```yaml
- uses: Wandalen/wretry.action@master
  with:
    command: npm i
    attempt_limit: 3
    attempt_delay: 2000
```

### Development and contributing

To build compiled dependencies utility `willbe` is required. To install utility run :
```
npm i -g 'willbe@latest'
```

`willbe` is not required to use the action in your project as submodule.

<!-- will .publish action.release.minor -->
