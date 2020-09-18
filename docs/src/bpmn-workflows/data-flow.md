# Data Flow

Every BPMN workflow instance can have one or more variables. Variables are key-value-pairs and hold
the contextual data of the workflow instance that is required by job workers to do their work or to
decide which sequence flows to take. They can be provided when a workflow instance is created, when
a job is completed, and when a message is correlated.

![data-flow](/bpmn-workflows/data-flow.png)

## Job Workers

By default, a job worker gets all variables of a workflow instance. It can limit the data by
providing a list of required variables as *fetchVariables*.

The worker uses the variables to do its work. When the work is done, it completes the job. If the
result of the work is needed by follow-up tasks, then the worker sets the variables while completing
the job. These variables are [merged](/reference/variables.html#variable-propagation) into the
workflow instance.

![job-worker](/bpmn-workflows/data-flow-job-worker.png)

If the job worker expects the variables in a different format or under different names then the variables can be transformed by defining *input mappings* in the workflow. *Output mappings* can be used to transform the job variables before merging them into the workflow instance.

## Variable Scopes vs. Token-Based Data

A workflow can have concurrent paths, for example, when using a parallel gateway. When the execution reaches the parallel gateway then new tokens are spawned which execute the following paths concurrently.

Since the variables are part of the workflow instance and not of the token, they can be read globally from any token. If a token adds a variable or modifies the value of a variable then the changes are also visible to concurrent tokens.

![variable-scopes](/bpmn-workflows/variable-scopes.png)

The visibility of variables is defined by the *variable scopes* of the workflow.

## Concurrency considerations
When multiple active activities exist in a workflow instance (i.e. there is a form of concurrent
execution, e.g. usage of a parallel gateway, multiple outgoing sequence flows or a parallel
multi-instance marker), you may need to take extra care in dealing with variables. When variables
are altered by one activity, it might also be accessed and altered by another at the same time. Race
conditions can occur in such workflows.

We recommend taking care when writing variables in a parallel flow. Make sure the variables are
written to the correct [variable scope](/reference/variables.html#variable-scopes) using variable
mappings and make sure to complete jobs and publish messages only with the minimum required
variables.

These type of problems can be avoided by:
* passing only updated variables
* using output variable mappings to customize the variable propagation
* using an embedded subprocess and input variable mappings to limit the visibility and propagation of variables

## Additional Resources

* [Job Handling](/basics/job-workers.html)
* [Variables](/reference/variables.html)
* [Input/Output Variable Mappings](/reference/variables.html#inputoutput-variable-mappings)
* [Variable Scopes](/reference/variables.html#variable-scopes)
