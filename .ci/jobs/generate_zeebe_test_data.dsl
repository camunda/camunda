pipelineJob('generate-zeebe-test-data') {

  displayName 'Generate Zeebe test datasets'
  description 'Generates and stores Zeebe test datasets for use in other jobs'
  
  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
      disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/generate_zeebe_test_data.groovy'))
      sandbox()
    }
  }

  parameters {
      stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for Zeebe test datasets generation.')
      stringParam('ES_VERSION', '7.10.0', 'Elasticsearch version to use.')
      stringParam('ZEEBE_VERSION', '1.3.0', 'Zeebe version to use.')
      stringParam('ZEEBE_PARTITION_COUNT', '3', 'Number of Zeebe partitions to configure.')
      stringParam('INSTANCE_STARTER_THREAD_COUNT', '8', 'Threadcount for pool of instance starters.')
      stringParam('JOBWORKER_MAX_ACTIVE_COUNT', '5', 'Max. amount of active jobs for jobworkers to complete tasks of deployed instances.')
      stringParam('JOBWORKER_EXECUTION_THREAD_COUNT', '1', 'Amount of jobworker execution threads to complete tasks of deployed instances.')
      stringParam('NUM_PROCESS_INSTANCES', '1000000', 'Number of process instances to generate, instances will be spread evenly among definitions.')
      stringParam('NUM_PROCESS_DEFINITIONS', '100', 'Number of process definitions to deploy.')
  }
}
