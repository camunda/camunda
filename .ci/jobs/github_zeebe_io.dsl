// vim: set filetype=groovy:

organizationFolder('zeebe-io') {

    description('Jobs for https://github.com/zeebe-io')
    displayName('zeebe-io')

    organizations {
        github {
            repoOwner('zeebe-io')
            credentialsId('camunda-jenkins-github')

            traits {
                 cleanBeforeCheckoutTrait()
                 gitHubBranchDiscovery {
                    strategyId(3)
                 }
                 pruneStaleBranchTrait()
            }
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            numToKeep(10)
        }
    }

    triggers {
        periodicFolderTrigger {
            interval('8h')
        }
    }

    configure {

        def traits = it / navigators / 'org.jenkinsci.plugins.github__branch__source.GitHubSCMNavigator' / traits
        traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
            strategyId 2
        }

    }

}

