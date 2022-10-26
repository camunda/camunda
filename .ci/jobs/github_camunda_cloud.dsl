// vim: set filetype=groovy:

organizationFolder('camunda-cloud') {

    description('Jobs for https://github.com/camunda-cloud')
    displayName('camunda-cloud')

    organizations {
        github {
            repoOwner('camunda-cloud')
            credentialsId('github-cloud-zeebe-app')

            traits {
                cleanBeforeCheckoutTrait {
                    extension {
                        deleteUntrackedNestedRepositories(false)
                    }
                }
                // Discover branches.
                // Strategy ID 3 => Discover all branches.
                gitHubBranchDiscovery {
                    strategyId 3
                }
                pruneStaleBranchTrait()
                localBranchTrait()
                sourceWildcardFilter {
                  includes('zeebe*')
                  excludes('')
                }

                // Disable sending Github status notifications in non-prod envs.
                if (ENVIRONMENT != 'prod') {
                    githubSkipNotifications()
                }
            }
        }
    }

    buildStrategies {
        // Don't auto build discovered branches for non prod envs.
        if (ENVIRONMENT == 'prod') {
            // Builds regular branches whenever a change is detected.
            buildRegularBranches()
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            numToKeep 10
        }
    }

    triggers {
        periodicFolderTrigger {
            interval '8h'
        }
    }

    properties {
        // Avoid automatically build jobs on non-prod envs by org indexing.
        // Note: The DSL name here is misleading. This config is for the branches that WILL be built automatically.
        // So on prod env all branches will be built automatically but for non-prod no automatic builds.
        noTriggerOrganizationFolderProperty {
            branches (ENVIRONMENT == 'prod' ? '.*' : '')
        } 
    }

}
