/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */

// https://github.com/camunda/jenkins-global-shared-library
@Library('camunda-ci') _

import org.camunda.helper.GitUtilities

camundaGitHubWorkflowDispatch([
        cloud: 'optimize-ci',
        credentialsId: 'github-optimize-app',
        dryRun: params.DRY_RUN,
        inputs: [
                app_name: GitUtilities.getSanitizedBranchName([BRANCH_NAME: params.BRANCH],50),
                chart_ref: params.BRANCH,
                es_version: params.ES_VERSION,
                cambpm_version: params.CAMBPM_VERSION,
                identity_version: params.IDENTITY_VERSION,
                zeebe_version: params.ZEEBE_VERSION
        ],
        org: 'camunda',
        ref: params.REF,
        repo: 'camunda-optimize',
        workflow: 'deploy-env.yml',
])
