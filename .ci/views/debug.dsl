listView('Debug') {

  configure { view ->

    // job filters
    view / jobFilters << {
      'hudson.views.AllJobsFilter' {}
    }

    // columns
    view / columns << {
      'com.robestone.hudson.compactcolumns.LastSuccessAndFailedColumn' {
        timeAgoTypeString 'DIFF'
      }
      'hudson.views.JobColumn' {}
      'hudson.views.BuildButtonColumn' {}
      'jenkins.plugins.extracolumns.LastBuildConsoleColumn' {}
      'jenkins.plugins.extracolumns.ConfigureProjectColumn' {}
      'jenkins.plugins.extracolumns.DisableProjectColumn' {
        useIcon true
      }
      'jenkins.plugins.extracolumns.BuildDurationColumn' {
        buildDurationType 1
      }
      'jenkins.plugins.extracolumns.JobTypeColumn' {
        usePronoun false
      }
      'jenkins.plugins.extracolumns.SlaveOrLabelColumn' {}
      'org.camunda.jenkins.SCMTypeColumn' {}
      'hudson.plugins.CronViewColumn' {}
      'org.camunda.jenkins.JobDSLConfigModifiedColumn' {}
      'jenkins.plugins.extracolumns.LastJobConfigurationModificationColumn' {}
      'jenkins.plugins.extracolumns.BuildParametersColumn' {
        singlePara false
      }
    }
  }
}
