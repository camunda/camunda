listView('Distro-Jobs') {
  jobs {
    regex '.*maven-deploy.*'
  }

  configure { view ->
    // columns
    view / columns << {
      'hudson.views.StatusColumn' {}
      'hudson.views.WeatherColumn' {}
      'hudson.views.BuildButtonColumn' {}
      'hudson.plugins.release.ReleaseButtonColumn' {}
      'jenkins.plugins.extracolumns.LastBuildConsoleColumn' {}
      'jenkins.plugins.extracolumns.ConfigureProjectColumn' {}
      'hudson.views.JobColumn' {}
      'com.robestone.hudson.compactcolumns.LastSuccessAndFailedColumn' {
        timeAgoTypeString 'DIFF'
      }
      'jenkins.plugins.extracolumns.BuildDurationColumn' {
        buildDurationType 1
      }
    }
  }
}
