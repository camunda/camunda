# Data Flow

Every BPMN workflow instance can have one or more variables. Variables are key-value-pairs and hold the contextual data of the workflow instance that is required by job workers to do their work. They can be provided when a workflow instance is created, when a job is completed, and when a message is correlated.

![data-flow](/bpmn-workflows/data-flow.png)

## Job Workers

By default, a job worker gets all variables of a workflow instance. It can limit the data by providing a list of required variables as *fetchVariables*.

The worker uses the variables to do its work. When the work is done, it completes the job. If the result of the work is needed by follow-up tasks, then the worker set the variables while completing the job. These variables are merged into the workflow instance.

![job-worker](/bpmn-workflows/data-flow-job-worker.png)

If the job worker expects the variables in a different format or under different names then the variables can be transformed by defining *input mappings* in the workflow. *Output mappings* can be used to transform the job variables before merging them into the workflow instance.

## Variable Scopes vs. Token-Based Data

A workflow can have concurrent paths, for example, when using a parallel gateway. When the execution reaches the parallel gateway then new tokens are spawned which executes the following paths concurrently.

Since the variables are part of the workflow instance and not of the token, they can be read globally from any token. If a token adds a variable or modifies the value of a variable then the changes are also visible to concurrent tokens.  

![variable-scopes](/bpmn-workflows/variable-scopes.png)

The visibility of variables is defined by the *variable scopes* of the workflow.

## Additional Resources

* [Job Handling](basics/job-workers.html)
* [Variables](reference/variables.html)
* [Input/Output Variable Mappings](reference/variables.html#inputoutput-variable-mappings)
* [Variable Scopes](reference/variables.html#variable-scopes)
