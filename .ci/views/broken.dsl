import javaposse.jobdsl.dsl.views.jobfilter.MatchType
import javaposse.jobdsl.dsl.views.jobfilter.Status

listView('Broken') {

  jobFilters {
    status {
      matchType(MatchType.INCLUDE_MATCHED)
      status(Status.FAILED,Status.UNSTABLE,Status.ABORTED)
    }
  }

  recurse(true)

  configure { view ->

    // columns
    view / columns << {
      'hudson.views.StatusColumn' {}
      'hudson.views.WeatherColumn' {}
      'hudson.views.BuildButtonColumn' {}
      'jenkins.plugins.extracolumns.LastBuildConsoleColumn' {}
      'jenkins.plugins.extracolumns.ConfigureProjectColumn' {}
      'hudson.views.JobColumn' {}
      'com.robestone.hudson.compactcolumns.LastSuccessAndFailedColumn' {
        timeAgoTypeString 'DIFF'
      }
      'jenkins.plugins.extracolumns.BuildDurationColumn' {
        buildDurationType 1
      }
      'jenkins.plugins.extracolumns.BuildDurationColumn' {
        buildDurationType 0
      }
      'jenkins.plugins.extracolumns.TestResultColumn' {
        testResultFormat 0
      }
    }
  }
}
